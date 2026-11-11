package com.localmind.conversation.dto;

import com.localmind.conversation.entity.ChatMessage;
import java.time.Instant;

public record MessageResponse(String id, String conversationId, String role, String content, String reasoningContent,
        String toolCalls, Integer tokenCount, Instant createdAt) {

    public static MessageResponse from(ChatMessage message) {
        return new MessageResponse(message.getId(), message.getConversationId(),
                message.getRole() != null ? message.getRole().name() : null, message.getContent(),
                message.getReasoningContent(), message.getToolCalls(), message.getTokenCount(), message.getCreatedAt());
    }
}
