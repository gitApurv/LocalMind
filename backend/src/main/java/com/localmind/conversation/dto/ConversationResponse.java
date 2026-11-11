package com.localmind.conversation.dto;

import com.localmind.conversation.entity.Conversation;
import java.time.Instant;

public record ConversationResponse(String id, String userId, String title, String modelName, boolean pinned,
        Instant createdAt, Instant updatedAt) {

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(conversation.getId(), conversation.getUserId(), conversation.getTitle(),
                conversation.getModelName(), conversation.isPinned(), conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }
}
