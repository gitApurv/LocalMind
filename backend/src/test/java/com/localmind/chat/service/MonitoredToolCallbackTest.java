package com.localmind.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.localmind.chat.dto.ChatEvent;
import com.localmind.chat.model.ToolCallRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link MonitoredToolCallback}: TOOL_START/TOOL_END events, result summaries and failures recorded in
 * the audit trail, and the per-turn loop limit.
 */
class MonitoredToolCallbackTest {

    @Test
    @DisplayName("A tool that throws is still recorded with a result summary for the audit trail")
    @SuppressWarnings("unchecked")
    void failedCallIsRecordedWithSummary() {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder().name("searchWeb").description("d")
                .inputSchema("{}").build());
        when(delegate.call(any(), any())).thenThrow(new IllegalStateException("boom"));
        List<ToolCallRecord> audit = new ArrayList<>();
        MonitoredToolCallback callback = new MonitoredToolCallback(delegate, mock(FluxSink.class), new AtomicInteger(),
                audit);

        String result = callback.call("{}", null);

        assertEquals("Error executing tool (searchWeb). Continue without its result.", result);
        assertEquals(1, audit.size());
        ToolCallRecord recorded = audit.getFirst();
        assertEquals("searchWeb", recorded.tool());
        assertEquals("{}", recorded.input());
        assertNotNull(recorded.timestamp());
        assertEquals(MonitoredToolCallback.FAILED_SUMMARY, recorded.resultSummary());
    }

    @Test
    @DisplayName("MonitoredToolCallback should record resultSummary in executedToolCalls")
    void wrapToolRecordsResultSummary() {
        AtomicInteger counter = new AtomicInteger(0);
        List<ToolCallRecord> executedToolCalls = new ArrayList<>();
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.call(any(), any())).thenReturn("Search returned 3 results: result A, result B, result C");

        @SuppressWarnings("unchecked")
        FluxSink<ChatEvent> mockSink = mock(FluxSink.class);
        ToolCallback wrapped = new MonitoredToolCallback(delegate, mockSink, counter, executedToolCalls);
        wrapped.call("test query", null);

        assertEquals(1, executedToolCalls.size());
        assertTrue(executedToolCalls.getFirst().resultSummary().contains("Search returned 3 results"));
    }

    @Test
    @DisplayName("MonitoredToolCallback should emit TOOL_START and TOOL_END events and break loops after 5 iterations")
    void wrapToolEmitsEventsAndBreaksLoop() {
        AtomicInteger counter = new AtomicInteger(0);
        List<ToolCallRecord> executedToolCalls = new ArrayList<>();
        Flux<ChatEvent> flux = Flux.create(sink -> {
            ToolCallback mockCallback = FunctionToolCallback
                    .builder("testTool", (Map<String, Object> input) -> "result").description("test tool description")
                    .inputType(Map.class).build();

            ToolCallback wrapped = new MonitoredToolCallback(mockCallback, sink, counter, executedToolCalls);

            for (int i = 0; i < 5; i++) {
                String res = wrapped.call("{}");
                assertTrue(res.contains("result"));
            }

            String blocked = wrapped.call("{}");
            assertTrue(blocked.contains("Loop protection triggered"));

            sink.complete();
        });

        StepVerifier.create(flux)
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.TOOL_START && "testTool".equals(e.tool()))
                .expectNextMatches(e -> e.type() == ChatEvent.EventType.TOOL_END && "testTool".equals(e.tool()))
                .expectNextCount(8).verifyComplete();

        assertEquals(6, counter.get());
        assertEquals(5, executedToolCalls.size());
    }
}
