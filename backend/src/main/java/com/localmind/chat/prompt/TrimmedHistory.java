package com.localmind.chat.prompt;

import com.localmind.conversation.entity.ChatMessage;
import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * Result of fitting conversation history into a token budget.
 *
 * @param messages
 *            the newest turns that fit, oldest first, ready for the prompt
 * @param dropped
 *            the older turns that did not fit, oldest first; candidates for the rolling summary
 */
public record TrimmedHistory(List<Message> messages, List<ChatMessage> dropped) {

    public static TrimmedHistory empty() {
        return new TrimmedHistory(List.of(), List.of());
    }
}
