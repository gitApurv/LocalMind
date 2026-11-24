package com.localmind.conversation.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/conversations}: optional title and model name. The body itself may be omitted; a
 * missing or blank title becomes the placeholder {@code "New Chat"} and a missing model the configured default model.
 */
public record CreateConversationRequest(@Size(max = 255, message = "Title cannot exceed 255 characters") String title,
        @Size(max = 100, message = "Model name cannot exceed 100 characters") String modelName) {
}
