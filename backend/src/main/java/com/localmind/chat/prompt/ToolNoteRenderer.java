package com.localmind.chat.prompt;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders the tool-call audit JSON stored on an assistant message as a compact note appended to that message when it is
 * replayed as history.
 */
@Component
@RequiredArgsConstructor
public class ToolNoteRenderer {

    private static final TypeReference<List<Map<String, Object>>> TOOL_CALLS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public String renderNote(String toolCallsJson) {
        if (toolCallsJson == null || toolCallsJson.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> calls = objectMapper.readValue(toolCallsJson, TOOL_CALLS_TYPE);
            if (calls == null || calls.isEmpty()) {
                return null;
            }
            StringBuilder note = new StringBuilder("\n[used tools: ");
            for (int i = 0; i < calls.size(); i++) {
                Map<String, Object> call = calls.get(i);
                if (i > 0) {
                    note.append(", ");
                }
                note.append(call.getOrDefault("tool", "tool"));
                if (call.containsKey("resultSummary")) {
                    note.append(" -> ").append(call.get("resultSummary"));
                }
            }
            return note.append("]").toString();
        } catch (Exception e) {
            return null;
        }
    }
}
