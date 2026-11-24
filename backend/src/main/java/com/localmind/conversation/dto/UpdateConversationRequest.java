package com.localmind.conversation.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body of {@code PATCH /api/conversations/{id}}: optional new title, pin state and model name. A {@code null}
 * field is left unchanged; a blank title is rejected with {@code 400} and a blank model name is ignored.
 */
public record UpdateConversationRequest(@Size(max = 255, message = "Title cannot exceed 255 characters") String title,
        Boolean pinned, @Size(max = 100, message = "Model name cannot exceed 100 characters") String modelName) {
}
