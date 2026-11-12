package com.localmind.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A server-sent event emitted while a chat response streams.
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
