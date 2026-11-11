package com.localmind.tools.service;

import com.localmind.common.ai.ChatContextKeys;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Reads the tenant identifiers the orchestrator places in the tool context.
 */
final class ToolContexts {

    private ToolContexts() {
    }

    static String get(ToolContext toolContext, String key) {
        return toolContext != null ? ChatContextKeys.getString(toolContext.getContext(), key) : null;
    }
}
