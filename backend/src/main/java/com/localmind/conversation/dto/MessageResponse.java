package com.localmind.conversation.dto;

import com.localmind.conversation.entity.ChatMessage;
import java.time.Instant;

/**
 * One persisted message in the {@code GET /api/conversations/{id}/messages} response, oldest first. {@code role} is
 * {@code USER} or {@code ASSISTANT}, and {@code toolCalls} is the raw JSON stored in {@code messages.tool_calls}.
 */
public record MessageResponse(String id, String conversationId, String role, String content, String reasoningContent,
        String toolCalls, Integer tokenCount, Instant createdAt) {

    public static MessageResponse from(ChatMessage message) {
        String role = message.getRole() != null ? message.getRole().name() : null;
        return new MessageResponse(message.getId(), message.getConversationId(), role, message.getContent(),
                message.getReasoningContent(), message.getToolCalls(), message.getTokenCount(), message.getCreatedAt());
    }
}
