package com.localmind.chat.prompt.history;

import com.localmind.chat.model.ToolCallRecord;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders the tool-call audit JSON stored on an assistant message as a compact note appended to that message when it is
 * replayed as history.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolNoteRenderer {

    private static final TypeReference<List<ToolCallRecord>> TOOL_CALLS_TYPE = new TypeReference<>() {
    };
    private static final String UNNAMED_TOOL = "tool";

    private final ObjectMapper objectMapper;

    public String renderNote(String toolCallsJson) {
        if (toolCallsJson == null || toolCallsJson.isBlank()) {
            return null;
        }
        try {
            List<ToolCallRecord> calls = objectMapper.readValue(toolCallsJson, TOOL_CALLS_TYPE);
            if (calls == null || calls.isEmpty()) {
                return null;
            }
            String used = calls.stream().map(ToolNoteRenderer::describe).collect(Collectors.joining(", "));
            return "\n[used tools: " + used + "]";
        } catch (RuntimeException e) {
            log.debug("Could not read stored tool calls; replaying the message without a note", e);
            return null;
        }
    }

    private static String describe(ToolCallRecord call) {
        String tool = call.tool() != null ? call.tool() : UNNAMED_TOOL;
        return call.resultSummary() != null ? tool + " -> " + call.resultSummary() : tool;
    }
}
