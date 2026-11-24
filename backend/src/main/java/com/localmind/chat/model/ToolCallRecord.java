package com.localmind.chat.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * One tool call of a chat turn. The list of a turn's calls is stored as the assistant message's {@code tool_calls} JSON
 * and replayed as a {@code [used tools: ...]} note when the message is sent again as history. Unknown properties are
 * ignored so rows written in an older format still render.
 *
 * @param tool
 *            name of the tool
 * @param input
 *            raw argument JSON the model sent
 * @param timestamp
 *            when the call started
 * @param resultSummary
 *            the result cut to a short preview, {@code "empty"} for no result or {@code "failed"} when the tool threw
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCallRecord(String tool, String input, Instant timestamp, String resultSummary) {
}
