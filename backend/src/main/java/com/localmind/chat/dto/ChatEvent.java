package com.localmind.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One server-sent event in the {@code POST /api/chat/stream} response. Fields that do not apply to the event type are
 * {@code null} and omitted from the JSON.
 *
 * @param content
 *            streamed text for {@code CONTENT} and {@code REASONING}, the user-facing message for {@code ERROR}
 * @param tool
 *            tool name for {@code TOOL_START} and {@code TOOL_END}
 * @param messageId
 *            id of the saved assistant message, sent with {@code DONE}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatEvent(EventType type, String content, String tool, String messageId) {

    public enum EventType {
        TOOL_START, TOOL_END, REASONING, CONTENT, ERROR, DONE
    }

    public static ChatEvent content(String text) {
        return new ChatEvent(EventType.CONTENT, text, null, null);
    }

    public static ChatEvent reasoning(String text) {
        return new ChatEvent(EventType.REASONING, text, null, null);
    }

    public static ChatEvent toolStart(String toolName) {
        return new ChatEvent(EventType.TOOL_START, null, toolName, null);
    }

    public static ChatEvent toolEnd(String toolName) {
        return new ChatEvent(EventType.TOOL_END, null, toolName, null);
    }

    public static ChatEvent error(String errorMessage) {
        return new ChatEvent(EventType.ERROR, errorMessage, null, null);
    }

    public static ChatEvent done(String messageId) {
        return new ChatEvent(EventType.DONE, null, null, messageId);
    }
}
