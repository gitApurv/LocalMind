package com.localmind.chat.service;

import com.localmind.chat.dto.ChatEvent;
import com.localmind.chat.dto.ChatRequest;
import com.localmind.chat.prompt.PromptAdvisor;
import com.localmind.chat.prompt.token.TokenCounter;
import com.localmind.conversation.entity.ChatMessage;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ChatMessageRepository;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.conversation.service.AutoTitleService;
import com.localmind.conversation.service.OwnershipGuard;
import com.localmind.llm.config.LlmProperties;
import com.localmind.llm.service.ModelProfileRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * Starts a streamed chat turn: checks ownership, resolves the model, persists the user message and hands the stream to
 * a new {@link ChatTurn}, which assembles the prompt through the {@link PromptAdvisor} pipeline and persists the reply.
 */
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

    @Transactional
    public Flux<ChatEvent> streamChat(ChatRequest request, String userId) {
        Conversation conversation = ownershipGuard.lockOwnedConversation(request.conversationId(), userId);
        String model = llmProperties.resolveModel(request.model(), conversation.getModelName());
        ChatMessage userMessage = persistUserMessage(request);
        ChatTurn turn = new ChatTurn(request, userId, model, modelProfileRegistry.getProfile(model), userMessage,
                chatModel, promptAdvisors, toolSelector, chatMessageRepository, conversationRepository,
                autoTitleService, tokenCounter, objectMapper);
        return Flux.create(turn::start);
    }

    private ChatMessage persistUserMessage(ChatRequest request) {
        String content = request.message().trim();
        return chatMessageRepository.save(ChatMessage.builder().conversationId(request.conversationId())
                .role(ChatMessage.Role.USER).content(content).tokenCount(Math.max(1, tokenCounter.estimate(content)))
                .build());
    }
}
