package com.localmind.chat.prompt;

import com.localmind.chat.config.PromptProperties;
import com.localmind.common.ai.ChatContextKeys;
import com.localmind.conversation.entity.ChatMessage;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ChatMessageRepository;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.conversation.service.ConversationSummaryService;
import com.localmind.llm.model.ModelProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * Step 4: inserts the rolling summary and the token-trimmed conversation history (excluding the current user message)
 * between the system message and the current user message. Turns that no longer fit are handed to
 * {@link ConversationSummaryService} so they live on in the summary.
 */
@Slf4j
@Component
public class HistoryAdvisor implements PromptAdvisor {

    private static final int ORDER = 400;
    private static final int DEFAULT_HISTORY_BUDGET = 3400;

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final HistoryTrimmer historyTrimmer;
    private final ConversationSummaryService conversationSummaryService;
    private final boolean enabled;

    public HistoryAdvisor(ChatMessageRepository chatMessageRepository, ConversationRepository conversationRepository,
            HistoryTrimmer historyTrimmer, ConversationSummaryService conversationSummaryService,
            PromptProperties properties) {
        this.chatMessageRepository = chatMessageRepository;
        this.conversationRepository = conversationRepository;
        this.historyTrimmer = historyTrimmer;
        this.conversationSummaryService = conversationSummaryService;
        this.enabled = properties.history().tokenTrim();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        if (!enabled || request == null || request.context() == null) {
            return request;
        }
        String conversationId = PromptAdvisor.contextString(request, ChatContextKeys.CONVERSATION_ID);
        if (conversationId == null) {
            return request;
        }

        ModelProfile profile = PromptAdvisor.modelProfile(request);
        TrimmedHistory history = historyTrimmer.trimToBudget(
                loadHistory(conversationId, PromptAdvisor.contextString(request, ChatContextKeys.CURRENT_MESSAGE_ID)),
                profile != null ? profile.historyBudget() : DEFAULT_HISTORY_BUDGET);
        if (!history.dropped().isEmpty()) {
            conversationSummaryService.summarizeDroppedTurnsAsync(conversationId, history.dropped(),
                    profile != null ? profile.modelName() : null);
        }
        Optional<Message> summary = summaryMessage(conversationId);
        if (history.messages().isEmpty() && summary.isEmpty()) {
            return request;
        }

        Prompt prompt = request.prompt();
        List<Message> instructions = prompt != null && prompt.getInstructions() != null
                ? prompt.getInstructions()
                : List.of();
        // System instructions stay first; summary and history go before the current turn.
        List<Message> messages = new ArrayList<>();
        instructions.stream().filter(HistoryAdvisor::isSystem).forEach(messages::add);
        summary.ifPresent(messages::add);
        messages.addAll(history.messages());
        instructions.stream().filter(message -> !isSystem(message)).forEach(messages::add);
        return request.mutate().prompt(new Prompt(messages, prompt != null ? prompt.getOptions() : null)).build();
    }

    private static boolean isSystem(Message message) {
        return message.getMessageType() == MessageType.SYSTEM;
    }

    private List<ChatMessage> loadHistory(String conversationId, String currentMessageId) {
        return currentMessageId != null
                ? chatMessageRepository.findByConversationIdAndIdNotOrderByCreatedAtAsc(conversationId,
                        currentMessageId)
                : chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /** The summary is best-effort: a lookup failure must not block the chat. */
    private Optional<Message> summaryMessage(String conversationId) {
        try {
            return conversationRepository.findById(conversationId).filter(Conversation::hasSummary)
                    .map(conversation -> new SystemMessage(
                            "<summary>\n" + conversation.getSummary().trim() + "\n</summary>"));
        } catch (Exception e) {
            log.debug("Could not load summary for conversation '{}': {}", conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
