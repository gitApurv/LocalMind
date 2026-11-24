package com.localmind.chat.prompt.advisor;

import com.localmind.chat.config.PromptProperties;
import com.localmind.chat.prompt.PromptAdvisor;
import com.localmind.chat.prompt.history.HistoryTrimmer;
import com.localmind.chat.prompt.history.TrimmedHistory;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * Prompt step 4 (order 400): inserts the rolling summary and the token-trimmed conversation history (excluding the
 * current user message) between the system message and the current user message. Turns that no longer fit are handed to
 * {@link ConversationSummaryService} so they live on in the summary. Switched off by
 * {@code localmind.prompt.history.token-trim}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryAdvisor implements PromptAdvisor {

    private static final int ORDER = 400;

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final HistoryTrimmer historyTrimmer;
    private final ConversationSummaryService conversationSummaryService;
    private final PromptProperties properties;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientRequest apply(ChatClientRequest request, AdvisorChain chain) {
        if (!properties.history().tokenTrim() || request == null || request.context() == null) {
            return request;
        }
        String conversationId = PromptAdvisor.contextString(request, ChatContextKeys.CONVERSATION_ID);
        if (conversationId == null) {
            return request;
        }

        ModelProfile profile = PromptAdvisor.modelProfile(request);
        String currentMessageId = PromptAdvisor.contextString(request, ChatContextKeys.CURRENT_MESSAGE_ID);
        List<ChatMessage> loaded = loadHistory(conversationId, currentMessageId);
        int historyBudget = PromptAdvisor.budgetProfile(request).historyBudget();
        TrimmedHistory history = historyTrimmer.trimToBudget(loaded, historyBudget);
        if (!history.dropped().isEmpty()) {
            summarizeDropped(conversationId, history, profile);
        }
        Optional<Message> summary = summaryMessage(conversationId);
        if (history.messages().isEmpty() && summary.isEmpty()) {
            return request;
        }

        Prompt prompt = request.prompt();
        List<Message> instructions = prompt != null && prompt.getInstructions() != null
                ? prompt.getInstructions()
                : List.of();
        List<Message> messages = new ArrayList<>();
        instructions.stream().filter(HistoryAdvisor::isSystem).forEach(messages::add);
        summary.ifPresent(messages::add);
        messages.addAll(history.messages());
        instructions.stream().filter(message -> !isSystem(message)).forEach(messages::add);
        ChatOptions options = prompt != null ? prompt.getOptions() : null;
        Prompt historyPrompt = new Prompt(messages, options);
        return request.mutate().prompt(historyPrompt).build();
    }

    private void summarizeDropped(String conversationId, TrimmedHistory history, ModelProfile profile) {
        String modelName = profile != null ? profile.modelName() : null;
        try {
            conversationSummaryService.summarizeDroppedTurnsAsync(conversationId, history.dropped(), modelName);
        } catch (RuntimeException e) {
            log.warn("Could not start summarising dropped turns of conversation '{}'", conversationId, e);
        }
    }

    private static boolean isSystem(Message message) {
        return message.getMessageType() == MessageType.SYSTEM;
    }

    private List<ChatMessage> loadHistory(String conversationId, String currentMessageId) {
        if (currentMessageId == null) {
            return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        }
        return chatMessageRepository.findByConversationIdAndIdNotOrderByCreatedAtAsc(conversationId,
                currentMessageId);
    }

    private Optional<Message> summaryMessage(String conversationId) {
        try {
            return conversationRepository.findById(conversationId).filter(Conversation::hasSummary)
                    .map(HistoryAdvisor::toSummaryMessage);
        } catch (RuntimeException e) {
            log.warn("Could not load the summary of conversation '{}'; sending the prompt without it", conversationId,
                    e);
            return Optional.empty();
        }
    }

    private static Message toSummaryMessage(Conversation conversation) {
        String summaryText = "<summary>\n" + conversation.getSummary().trim() + "\n</summary>";
        return new SystemMessage(summaryText);
    }
}
