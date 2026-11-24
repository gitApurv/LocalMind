package com.localmind.chat.prompt.history;

import com.localmind.chat.prompt.token.TokenCounter;
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
 * Keeps the newest history that fits a token budget, preferring whole user/assistant pairs. User messages that never
 * got a reply are left out.
 */
@Component
@RequiredArgsConstructor
public class HistoryTrimmer {

    private final TokenCounter tokenCounter;
    private final ToolNoteRenderer toolNoteRenderer;

    public TrimmedHistory trimToBudget(List<ChatMessage> loadedHistory, int budgetTokens) {
        List<ChatMessage> history = withoutUnansweredPrompts(loadedHistory);
        if (history.isEmpty()) {
            return TrimmedHistory.empty();
        }
        if (budgetTokens <= 0) {
            List<ChatMessage> dropped = List.copyOf(history);
            return new TrimmedHistory(List.of(), dropped);
        }

        List<Message> converted = new ArrayList<>();
        int tokensUsed = 0;
        int i = history.size() - 1;
        while (i >= 0) {
            ChatMessage current = history.get(i);
            if (isAssistantReplyToUser(history, i)) {
                ChatMessage userMessage = history.get(i - 1);
                String assistantText = withToolNote(current);
                int userTokens = tokenCounter.estimate(userMessage.getContent());
                int assistantTokens = tokenCounter.estimate(assistantText);
                int pairTokens = userTokens + assistantTokens;
                if (tokensUsed + pairTokens > budgetTokens && !converted.isEmpty()) {
                    break;
                }
                Message assistantReply = new AssistantMessage(assistantText);
                Message userTurn = new UserMessage(userMessage.getContent());
                converted.addFirst(assistantReply);
                converted.addFirst(userTurn);
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
        List<ChatMessage> dropped = List.copyOf(history.subList(0, i + 1));
        return new TrimmedHistory(converted, dropped);
    }

    private static List<ChatMessage> withoutUnansweredPrompts(List<ChatMessage> history) {
        if (history == null) {
            return List.of();
        }
        List<ChatMessage> answered = new ArrayList<>(history.size());
        for (int i = 0; i < history.size(); i++) {
            ChatMessage message = history.get(i);
            boolean repliedTo = i + 1 < history.size() && history.get(i + 1).getRole() == ChatMessage.Role.ASSISTANT;
            if (message.getRole() != ChatMessage.Role.USER || repliedTo) {
                answered.add(message);
            }
        }
        return answered;
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
