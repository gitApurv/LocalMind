package com.localmind.common.ai;

import java.util.Map;

/**
 * Keys shared between the chat orchestrator, the prompt advisors (advisor context) and the LLM tools (tool context).
 */
public final class ChatContextKeys {

    public static final String USER_ID = "userId";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String CURRENT_MESSAGE_ID = "currentMessageId";
    public static final String MODEL_PROFILE = "modelProfile";
    public static final String REQUEST_SYSTEM_PROMPT = "requestSystemPrompt";

    private ChatContextKeys() {
    }

    /**
     * Returns the non-blank string stored under {@code key}, or {@code null}.
     */
    public static String getString(Map<String, ?> context, String key) {
        if (context == null) {
            return null;
        }
        return context.get(key) instanceof String value && !value.isBlank() ? value : null;
    }
}
