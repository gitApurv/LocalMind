package com.localmind.conversation.dto;

import com.localmind.conversation.entity.Conversation;
import java.time.Instant;

/**
 * Response body for one conversation, returned by {@code POST /api/conversations}, {@code GET /api/conversations} and
 * {@code PATCH /api/conversations/{id}}.
 */
public record ConversationResponse(String id, String userId, String title, String modelName, boolean pinned,
        Instant createdAt, Instant updatedAt) {

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(conversation.getId(), conversation.getUserId(), conversation.getTitle(),
                conversation.getModelName(), conversation.isPinned(), conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }
}
