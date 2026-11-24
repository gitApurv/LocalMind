package com.localmind.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.localmind.chat.model.ToolCallRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.FluxSink;

/**
 * Unit tests for {@link MonitoredToolCallback}: a failing tool returns a readable error and is still recorded in the
 * audit trail.
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
}
