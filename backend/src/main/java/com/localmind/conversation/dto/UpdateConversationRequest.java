package com.localmind.conversation.dto;

import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(@Size(max = 255, message = "Title cannot exceed 255 characters") String title,
        Boolean pinned, @Size(max = 100, message = "Model name cannot exceed 100 characters") String modelName) {
}
