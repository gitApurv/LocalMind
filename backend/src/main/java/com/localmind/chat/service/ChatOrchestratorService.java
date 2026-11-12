package com.localmind.chat.service;

import com.localmind.chat.dto.ChatEvent;
import com.localmind.chat.dto.ChatRequest;
import com.localmind.chat.prompt.PromptAdvisor;
import com.localmind.chat.prompt.TokenCounter;
import com.localmind.chat.prompt.ToolSelector;
import com.localmind.common.ai.ChatContextKeys;
import com.localmind.common.exception.ResourceNotFoundException;
import com.localmind.conversation.entity.ChatMessage;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ChatMessageRepository;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.conversation.service.AutoTitleService;
import com.localmind.conversation.service.OwnershipGuard;
import com.localmind.llm.config.LlmProperties;
import com.localmind.llm.model.ModelProfile;
import com.localmind.llm.service.ModelProfileRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import tools.jackson.databind.ObjectMapper;

/**
 * Streams a chat turn: persists the user message, assembles the prompt through the {@link PromptAdvisor} pipeline,
 * streams the model output as {@link ChatEvent}s and persists the assistant reply (or the partial reply on error or
 * client disconnect).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private final ChatModel chatModel;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final OwnershipGuard ownershipGuard;
    private final AutoTitleService autoTitleService;
    private final List<PromptAdvisor> promptAdvisors;
    private final ToolSelector toolSelector;
    private final ModelProfileRegistry modelProfileRegistry;
    private final LlmProperties llmProperties;
    private final TokenCounter tokenCounter;
    private final ObjectMapper objectMapper;

    public Flux<ChatEvent> streamChat(ChatRequest request, String userId) {
        // Ownership is verified before the reactive stream starts so errors map to HTTP
        // statuses.
        ownershipGuard.validateConversationOwnership(request.conversationId(), userId);
        Conversation conversation = conversationRepository.findById(request.conversationId()).orElseThrow(
                () -> new ResourceNotFoundException("Conversation not found: " + request.conversationId()));

        String model = llmProperties.resolveModel(request.model(), conversation.getModelName());
        ChatMessage userMessage = persistUserMessage(request);
        ChatTurn turn = new ChatTurn(request, userId, conversation, model, modelProfileRegistry.getProfile(model),
                userMessage);
        return Flux.create(turn::start);
    }

    /** Saved before streaming so the prompt is never lost. */
    private ChatMessage persistUserMessage(ChatRequest request) {
        String content = request.message().trim();
        return chatMessageRepository.save(ChatMessage.builder().conversationId(request.conversationId())
                .role(ChatMessage.Role.USER).content(content).tokenCount(estimateTokens(content)).build());
    }

    private int estimateTokens(String text) {
        return Math.max(1, tokenCounter.estimate(text));
    }

    /**
     * State of a single streaming chat turn.
     */
    private final class ChatTurn {

        private static final String TOOLS_UNSUPPORTED_MARKER = "does not support tools";
        // Details are logged; clients only get these messages so no internal error text leaks.
        private static final String START_FAILED_MESSAGE = "Failed to start the response. Please try again.";
        private static final String STREAM_FAILED_MESSAGE = "The model stopped responding. Check that Ollama is "
                + "running and the model is installed, then try again.";
        private static final String PERSIST_FAILED_MESSAGE = "The response could not be saved. Please try again.";

        private final ChatRequest request;
        private final String userId;
        private final Conversation conversation;
        private final String model;
        private final ModelProfile profile;
        private final ChatMessage userMessage;
        private final StreamingTagParser parser = new StreamingTagParser();
        private final List<Map<String, Object>> executedToolCalls = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger toolCallCounter = new AtomicInteger();
        private FluxSink<ChatEvent> sink;

        private ChatTurn(ChatRequest request, String userId, Conversation conversation, String model,
                ModelProfile profile, ChatMessage userMessage) {
            this.request = request;
            this.userId = userId;
            this.conversation = conversation;
            this.model = model;
            this.profile = profile;
            this.userMessage = userMessage;
        }

        void start(FluxSink<ChatEvent> sink) {
            this.sink = sink;
            try {
                Disposable upstream = buildPrompt().stream().chatResponse().subscribe(this::onChunk, this::onError,
                        this::onComplete);
                // Client disconnected: cancel the Ollama stream and keep what was generated.
                sink.onDispose(() -> {
                    if (!upstream.isDisposed()) {
                        upstream.dispose();
                        persistPartialAssistantMessage();
                    }
                });
            } catch (Exception e) {
                log.error("Failed to start chat streaming: {}", e.getMessage(), e);
                sink.next(ChatEvent.error(START_FAILED_MESSAGE));
                sink.complete();
            }
        }

        private ChatClient.ChatClientRequestSpec buildPrompt() {
            ChatClient.ChatClientRequestSpec spec = ChatClient.builder(chatModel).build().prompt()
                    .options(OllamaChatOptions.builder().model(model).numCtx(profile.numCtx())
                            .numPredict(profile.outputReserve()))
                    .advisors(new ArrayList<Advisor>(promptAdvisors))
                    .advisors(advisor -> advisorParams().forEach(advisor::param));
            registerTools(spec);
            return spec.user(request.message().trim());
        }

        private Map<String, Object> advisorParams() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put(ChatContextKeys.CONVERSATION_ID, request.conversationId());
            params.put(ChatContextKeys.USER_ID, userId);
            params.put(ChatContextKeys.MODEL_PROFILE, profile);
            if (userMessage != null && userMessage.getId() != null) {
                params.put(ChatContextKeys.CURRENT_MESSAGE_ID, userMessage.getId());
            }
            if (request.systemPrompt() != null) {
                params.put(ChatContextKeys.REQUEST_SYSTEM_PROMPT, request.systemPrompt());
            }
            return params;
        }

        private void registerTools(ChatClient.ChatClientRequestSpec spec) {
            List<ToolCallback> tools = toolSelector.selectTools(model, request.message(), request.conversationId(),
                    request.isToolsEnabled());
            if (tools.isEmpty()) {
                if (request.isToolsEnabled()) {
                    log.info("Tools requested for model '{}' which does not support tools in Ollama; skipping", model);
                }
                return;
            }
            List<ToolCallback> monitored = tools.stream().<ToolCallback>map(
                    tool -> new MonitoredToolCallback(tool, sink, toolCallCounter, executedToolCalls)).toList();
            spec.tools(monitored);
            spec.toolContext(
                    Map.of(ChatContextKeys.USER_ID, userId, ChatContextKeys.CONVERSATION_ID, request.conversationId()));
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
            log.error("Streaming error in chat for conversation '{}': {}", request.conversationId(),
                    error.getMessage(), error);
            persistPartialAssistantMessage();
            sink.next(ChatEvent.error(describeError(error)));
            sink.complete();
        }

        private void onComplete() {
            try {
                parser.flush(sink);
                ChatMessage saved = chatMessageRepository.save(assistantMessage());
                // Only bump updatedAt: saving the entity loaded at stream start would undo concurrent edits.
                conversationRepository.touch(conversation.getId(), Instant.now());
                sink.next(ChatEvent.done(saved.getId()));
                autoTitleService.generateTitleAsync(request.conversationId(), request.message(), model);
            } catch (Exception e) {
                log.error("Failed to persist message history for conversation '{}': {}", request.conversationId(),
                        e.getMessage(), e);
                sink.next(ChatEvent.error(PERSIST_FAILED_MESSAGE));
            } finally {
                sink.complete();
            }
        }

        private void persistPartialAssistantMessage() {
            if (parser.getAccumulatedContent().isBlank() && parser.getAccumulatedReasoning().isBlank()) {
                return;
            }
            try {
                chatMessageRepository.save(assistantMessage());
            } catch (Exception e) {
                log.warn("Failed to persist partial assistant message: {}", e.getMessage());
            }
        }

        private ChatMessage assistantMessage() {
            String content = parser.getAccumulatedContent();
            String reasoning = parser.getAccumulatedReasoning();
            return ChatMessage.builder().conversationId(request.conversationId()).role(ChatMessage.Role.ASSISTANT)
                    .content(content).reasoningContent(reasoning.isBlank() ? null : reasoning)
                    .toolCalls(serializeToolCalls()).tokenCount(estimateTokens(content + reasoning)).build();
        }

        private String serializeToolCalls() {
            List<Map<String, Object>> snapshot;
            synchronized (executedToolCalls) {
                if (executedToolCalls.isEmpty()) {
                    return null;
                }
                snapshot = new ArrayList<>(executedToolCalls);
            }
            try {
                return objectMapper.writeValueAsString(snapshot);
            } catch (Exception e) {
                log.warn("Failed to serialize tool calls: {}", e.getMessage());
                return null;
            }
        }

        private String describeError(Throwable error) {
            String details = collectMessages(error);
            if (details.contains(TOOLS_UNSUPPORTED_MARKER) || (details.contains("400") && details.contains("tools"))) {
                return "Model '" + model + "' does not support tool calling in Ollama. Please disable Tools or "
                        + "select a tool-compatible model (e.g., qwen2.5:7b).";
            }
            return STREAM_FAILED_MESSAGE;
        }

        /** Ollama's error text can be buried in causes or suppressed exceptions. */
        private static String collectMessages(Throwable error) {
            StringBuilder messages = new StringBuilder();
            for (Throwable current = error; current != null; current = current.getCause()) {
                if (current.getMessage() != null) {
                    messages.append(current.getMessage()).append(' ');
                }
                for (Throwable suppressed : current.getSuppressed()) {
                    if (suppressed.getMessage() != null) {
                        messages.append(suppressed.getMessage()).append(' ');
                    }
                }
            }
            return messages.toString();
        }
    }
}
