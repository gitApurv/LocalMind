package com.localmind.chat.prompt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.localmind.chat.model.ToolCallRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link ToolNoteRenderer}: rendering recorded tool calls with summaries, legacy rows, and no note for
 * missing or unreadable JSON.
 */
class ToolNoteRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolNoteRenderer renderer = new ToolNoteRenderer(objectMapper);

    @Test
    @DisplayName("Renders the tool calls recorded during a turn, in order, with their result summaries")
    void rendersRecordedCalls() {
        Instant startedAt = Instant.parse("2026-11-16T10:15:30.123Z");
        String json = objectMapper.writeValueAsString(
                List.of(new ToolCallRecord("searchWeb", "{\"query\":\"x\"}", startedAt, "3 results"),
                        new ToolCallRecord("calculateExpression", "{}", startedAt, "failed")));

        assertTrue(json.contains("\"timestamp\":\"2026-11-16T10:15:30.123Z\""), json);
        assertEquals("\n[used tools: searchWeb -> 3 results, calculateExpression -> failed]",
                renderer.renderNote(json));
    }

    @Test
    @DisplayName("Rows in an older format still render: unknown fields are ignored, a missing summary is omitted")
    void rendersLegacyRows() {
        String json = "[{\"tool\":\"searchWeb\",\"query\":\"quantum entanglement 2026\"}]";

        assertEquals("\n[used tools: searchWeb]", renderer.renderNote(json));
    }

    @Test
    @DisplayName("No note for missing, empty or unreadable tool-call JSON")
    void noNoteWithoutCalls() {
        assertNull(renderer.renderNote(null));
        assertNull(renderer.renderNote(" "));
        assertNull(renderer.renderNote("[]"));
        assertNull(renderer.renderNote("not json"));
    }
}
