package com.localmind.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "Conversation ID must not be blank") @Size(max = 36, message = "Conversation ID cannot exceed 36 characters") String conversationId,
        @NotBlank(message = "Message content must not be blank") @Size(max = 32000, message = "Message cannot exceed 32000 characters") String message,
        @Size(max = 100, message = "Model name cannot exceed 100 characters") String model, Boolean toolsEnabled,
        @Size(max = 8000, message = "System prompt cannot exceed 8000 characters") String systemPrompt) {

    /** Tools are on unless the client explicitly disables them. */
    public boolean isToolsEnabled() {
        return toolsEnabled == null || toolsEnabled;
    }
}
