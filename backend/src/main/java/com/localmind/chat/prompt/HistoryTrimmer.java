package com.localmind.chat.prompt;

import com.localmind.conversation.entity.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * Keeps the newest history that fits a token budget, preferring whole user/assistant pairs.
 */
@Component
@RequiredArgsConstructor
public class HistoryTrimmer {

    private final TokenCounter tokenCounter;
    private final ToolNoteRenderer toolNoteRenderer;

    public TrimmedHistory trimToBudget(List<ChatMessage> history, int budgetTokens) {
        if (history == null || history.isEmpty()) {
            return TrimmedHistory.empty();
        }
        if (budgetTokens <= 0) {
            return new TrimmedHistory(List.of(), List.copyOf(history));
        }

        List<Message> converted = new ArrayList<>();
        int tokensUsed = 0;
        int i = history.size() - 1;
        while (i >= 0) {
            ChatMessage current = history.get(i);
            if (isAssistantReplyToUser(history, i)) {
                ChatMessage userMessage = history.get(i - 1);
                String assistantText = withToolNote(current);
                int pairTokens = tokenCounter.estimate(userMessage.getContent()) + tokenCounter.estimate(assistantText);
                if (tokensUsed + pairTokens > budgetTokens && !converted.isEmpty()) {
                    break;
                }
                converted.addFirst(new AssistantMessage(assistantText));
                converted.addFirst(new UserMessage(userMessage.getContent()));
                tokensUsed += pairTokens;
                i -= 2;
            } else {
                int singleTokens = tokenCounter.estimate(current.getContent());
                if (tokensUsed + singleTokens > budgetTokens && !converted.isEmpty()) {
                    break;
                }
                toSpringMessage(current).ifPresent(converted::addFirst);
                tokensUsed += singleTokens;
                i--;
            }
        }
        // Everything at or before index i did not fit.
        return new TrimmedHistory(converted, List.copyOf(history.subList(0, i + 1)));
    }

    private static boolean isAssistantReplyToUser(List<ChatMessage> history, int index) {
        return history.get(index).getRole() == ChatMessage.Role.ASSISTANT && index > 0
                && history.get(index - 1).getRole() == ChatMessage.Role.USER;
    }

    private String withToolNote(ChatMessage assistantMessage) {
        String note = toolNoteRenderer.renderNote(assistantMessage.getToolCalls());
        return assistantMessage.getContent() + (note != null ? note : "");
    }

    private static Optional<Message> toSpringMessage(ChatMessage message) {
        return switch (message.getRole()) {
            case USER -> Optional.of(new UserMessage(message.getContent()));
            case ASSISTANT -> Optional.of(new AssistantMessage(message.getContent()));
            default -> Optional.empty();
        };
    }
}
