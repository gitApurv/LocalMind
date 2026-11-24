package com.localmind.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/chat/stream}: the conversation, the user's message and optional per-turn model,
 * tools switch and system prompt.
 *
 * @param model
 *            model for this turn; blank falls back to the conversation's model, then the configured default
 * @param toolsEnabled
 *            whether tools are offered; {@code null} means enabled
 * @param systemPrompt
 *            extra instructions added to the system message after the base rules
 */
public record ChatRequest(
        @NotBlank(message = "Conversation ID must not be blank") @Size(max = 36, message = "Conversation ID cannot exceed 36 characters") String conversationId,
        @NotBlank(message = "Message content must not be blank") @Size(max = 32000, message = "Message cannot exceed 32000 characters") String message,
        @Size(max = 100, message = "Model name cannot exceed 100 characters") String model, Boolean toolsEnabled,
        @Size(max = 8000, message = "System prompt cannot exceed 8000 characters") String systemPrompt) {

    public boolean isToolsEnabled() {
        return toolsEnabled == null || toolsEnabled;
    }
}
