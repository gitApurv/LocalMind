package com.localmind.chat.service;

import com.localmind.chat.dto.ChatEvent;
import com.localmind.chat.dto.ChatRequest;
import com.localmind.chat.model.ToolCallRecord;
import com.localmind.chat.prompt.PromptAdvisor;
import com.localmind.chat.prompt.token.TokenCounter;
import com.localmind.common.ai.ChatContextKeys;
import com.localmind.conversation.entity.ChatMessage;
import com.localmind.conversation.repository.ChatMessageRepository;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.conversation.service.AutoTitleService;
import com.localmind.llm.model.ModelProfile;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.Disposable;
import reactor.core.publisher.FluxSink;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs one streamed chat turn: builds the prompt, relays model output as {@link ChatEvent}s and saves the assistant
 * reply (or the partial reply on error or client disconnect). Created by {@link ChatOrchestratorService} once per
 * stream, so the sink, parser and tool-call audit list are never shared between turns.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ChatTurn {

    private static final String TOOLS_UNSUPPORTED_MARKER = "does not support tools";
    private static final String START_FAILED_MESSAGE = "Failed to start the response. Please try again.";
    private static final String STREAM_FAILED_MESSAGE = "The model stopped responding. Check that Ollama is "
            + "running and the model is installed, then try again.";
    private static final String PERSIST_FAILED_MESSAGE = "The response could not be saved. Please try again.";

    private final ChatRequest request;
    private final String userId;
    private final String model;
    private final ModelProfile profile;
    private final ChatMessage userMessage;
    private final ChatModel chatModel;
    private final List<PromptAdvisor> promptAdvisors;
    private final ToolSelector toolSelector;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final AutoTitleService autoTitleService;
    private final TokenCounter tokenCounter;
    private final ObjectMapper objectMapper;
    private final StreamingTagParser parser = new StreamingTagParser();
    private final List<ToolCallRecord> executedToolCalls = new CopyOnWriteArrayList<>();
    private final AtomicInteger toolCallCounter = new AtomicInteger();
    private FluxSink<ChatEvent> sink;

    void start(FluxSink<ChatEvent> sink) {
        this.sink = sink;
        try {
            Disposable upstream = buildPrompt().stream().chatResponse().subscribe(this::onChunk, this::onError,
                    this::onComplete);
            sink.onDispose(() -> {
                if (!upstream.isDisposed()) {
                    upstream.dispose();
                    persistPartialAssistantMessage();
                }
            });
        } catch (Exception e) {
            log.error("Failed to start chat streaming for conversation '{}'", conversationId(), e);
            sink.next(ChatEvent.error(START_FAILED_MESSAGE));
            sink.complete();
        }
    }

    private String conversationId() {
        return request.conversationId();
    }

    private ChatClient.ChatClientRequestSpec buildPrompt() {
        OllamaChatOptions.Builder options = OllamaChatOptions.builder().model(model).numCtx(profile.numCtx())
                .numPredict(profile.outputReserve());
        ChatClient.ChatClientRequestSpec spec = ChatClient.builder(chatModel).build().prompt().options(options)
                .advisors(List.copyOf(promptAdvisors)).advisors(advisor -> advisor.params(advisorParams()));
        registerTools(spec);
        return spec.user(userMessage.getContent());
    }

    private Map<String, Object> advisorParams() {
        Map<String, Object> params = new HashMap<>(Map.of(ChatContextKeys.CONVERSATION_ID, conversationId(),
                ChatContextKeys.USER_ID, userId, ChatContextKeys.MODEL_PROFILE, profile));
        if (userMessage.getId() != null) {
            params.put(ChatContextKeys.CURRENT_MESSAGE_ID, userMessage.getId());
        }
        if (request.systemPrompt() != null) {
            params.put(ChatContextKeys.REQUEST_SYSTEM_PROMPT, request.systemPrompt());
        }
        return params;
    }

    private void registerTools(ChatClient.ChatClientRequestSpec spec) {
        List<ToolCallback> tools = toolSelector.selectTools(model, userMessage.getContent(), conversationId(),
                request.isToolsEnabled());
        if (tools.isEmpty()) {
            if (request.isToolsEnabled()) {
                log.info("Tools requested for model '{}' which does not support tools in Ollama; skipping", model);
            }
            return;
        }
        spec.tools(tools.stream().<ToolCallback>map(
                tool -> new MonitoredToolCallback(tool, sink, toolCallCounter, executedToolCalls)).toList());
        spec.toolContext(Map.of(ChatContextKeys.USER_ID, userId, ChatContextKeys.CONVERSATION_ID, conversationId()));
    }

    private void onChunk(ChatResponse chunk) {
        if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return;
        }
        String text = chunk.getResult().getOutput().getText();
        if (text != null && !text.isEmpty()) {
            parser.processChunk(text, sink);
        }
    }

    private void onError(Throwable error) {
        log.error("Streaming error in chat for conversation '{}'", conversationId(), error);
        persistPartialAssistantMessage();
        sink.next(ChatEvent.error(mentionsUnsupportedTools(error)
                ? "Model '" + model + "' does not support tool calling in Ollama. Please disable Tools or "
                        + "select a tool-compatible model."
                : STREAM_FAILED_MESSAGE));
        sink.complete();
    }

    private static boolean mentionsUnsupportedTools(Throwable error) {
        return Stream.iterate(error, Objects::nonNull, Throwable::getCause)
                .flatMap(t -> Stream.concat(Stream.of(t), Stream.of(t.getSuppressed()))).map(Throwable::getMessage)
                .anyMatch(message -> message != null && message.contains(TOOLS_UNSUPPORTED_MARKER));
    }

    private void onComplete() {
        try {
            parser.flush(sink);
            ChatMessage saved = saveAssistantMessage();
            sink.next(ChatEvent.done(saved.getId()));
            autoTitleService.generateTitleAsync(conversationId(), userMessage.getContent(), model);
        } catch (Exception e) {
            log.error("Failed to persist message history for conversation '{}'", conversationId(), e);
            sink.next(ChatEvent.error(PERSIST_FAILED_MESSAGE));
        } finally {
            sink.complete();
        }
    }

    private void persistPartialAssistantMessage() {
        parser.flush(sink);
        if (parser.getAccumulatedContent().isBlank() && parser.getAccumulatedReasoning().isBlank()) {
            return;
        }
        try {
            saveAssistantMessage();
        } catch (Exception e) {
            log.warn("Failed to persist partial assistant message for conversation '{}'", conversationId(), e);
        }
    }

    private ChatMessage saveAssistantMessage() {
        String content = parser.getAccumulatedContent();
        String reasoning = parser.getAccumulatedReasoning();
        ChatMessage reply = ChatMessage.builder().conversationId(conversationId()).role(ChatMessage.Role.ASSISTANT)
                .content(content).reasoningContent(reasoning.isBlank() ? null : reasoning)
                .toolCalls(serializeToolCalls()).tokenCount(Math.max(1, tokenCounter.estimate(content))).build();
        ChatMessage saved = chatMessageRepository.save(reply);
        conversationRepository.touch(conversationId(), Instant.now());
        return saved;
    }

    private String serializeToolCalls() {
        List<ToolCallRecord> snapshot = List.copyOf(executedToolCalls);
        if (snapshot.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("Failed to serialize tool calls for conversation '{}'", conversationId(), e);
            return null;
        }
    }
}
