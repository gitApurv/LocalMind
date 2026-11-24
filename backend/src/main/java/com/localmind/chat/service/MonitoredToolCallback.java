package com.localmind.chat.service;

import com.localmind.chat.dto.ChatEvent;
import com.localmind.chat.model.ToolCallRecord;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import reactor.core.publisher.FluxSink;

/**
 * Decorates a tool so each call emits TOOL_START/TOOL_END events, is recorded for the audit trail, and is capped per
 * chat turn to break tool-call loops.
 */
@Slf4j
class MonitoredToolCallback implements ToolCallback {

    private static final int MAX_TOOL_ITERATIONS = 5;
    private static final String LOOP_PROTECTION_MESSAGE = "Loop protection triggered: Maximum tool calls ("
            + MAX_TOOL_ITERATIONS + ") reached. Please provide the final response to the user now.";
    private static final int RESULT_SUMMARY_CHARS = 120;
    static final String FAILED_SUMMARY = "failed";

    private final ToolCallback delegate;
    private final FluxSink<ChatEvent> sink;
    private final AtomicInteger callCounter;
    private final List<ToolCallRecord> executedToolCalls;

    MonitoredToolCallback(ToolCallback delegate, FluxSink<ChatEvent> sink, AtomicInteger callCounter,
            List<ToolCallRecord> executedToolCalls) {
        this.delegate = delegate;
        this.sink = sink;
        this.callCounter = callCounter;
        this.executedToolCalls = executedToolCalls;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = toolName();
        int iteration = callCounter.incrementAndGet();
        if (iteration > MAX_TOOL_ITERATIONS) {
            log.warn("Tool loop breaker activated for tool '{}' on iteration {}", toolName, iteration);
            return LOOP_PROTECTION_MESSAGE;
        }

        Instant startedAt = Instant.now();
        sink.next(ChatEvent.toolStart(toolName));
        String resultSummary = FAILED_SUMMARY;
        try {
            String result = delegate.call(toolInput, toolContext);
            resultSummary = summarize(result);
            return result;
        } catch (Exception e) {
            log.warn("Tool invocation failed for '{}': {}", toolName, e.getMessage(), e);
            return "Error executing tool (" + toolName + "). Continue without its result.";
        } finally {
            executedToolCalls.add(new ToolCallRecord(toolName, toolInput, startedAt, resultSummary));
            sink.next(ChatEvent.toolEnd(toolName));
        }
    }

    private String toolName() {
        return delegate.getToolDefinition() != null ? delegate.getToolDefinition().name() : "unknown";
    }

    private static String summarize(String result) {
        if (result == null) {
            return "empty";
        }
        return result.length() > RESULT_SUMMARY_CHARS ? result.substring(0, RESULT_SUMMARY_CHARS) + "..." : result;
    }
}
