package com.localmind.chat.service;

import com.localmind.chat.dto.ChatEvent;
import reactor.core.publisher.FluxSink;

/**
 * Splits a streamed completion into reasoning ({@code <think>...</think>}) and content events, buffering partial tags
 * that are cut across chunk boundaries.
 */
class StreamingTagParser {

    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";

    private boolean insideThink;
    private final StringBuilder thinkBuffer = new StringBuilder();
    private final StringBuilder contentBuffer = new StringBuilder();
    private String pending = "";

    synchronized void processChunk(String chunk, FluxSink<ChatEvent> sink) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        String text = pending + chunk;
        pending = "";
        while (!text.isEmpty()) {
            text = consume(text, sink);
        }
    }

    synchronized void flush(FluxSink<ChatEvent> sink) {
        emit(pending, sink);
        pending = "";
    }

    private String consume(String text, FluxSink<ChatEvent> sink) {
        String tag = insideThink ? THINK_CLOSE : THINK_OPEN;
        int tagStart = text.indexOf(tag);
        if (tagStart != -1) {
            emit(text.substring(0, tagStart), sink);
            insideThink = !insideThink;
            return text.substring(tagStart + tag.length());
        }
        int partialStart = findPartialTagStart(text, tag);
        int emitEnd = partialStart != -1 ? partialStart : text.length();
        emit(text.substring(0, emitEnd), sink);
        pending = text.substring(emitEnd);
        return "";
    }

    private void emit(String text, FluxSink<ChatEvent> sink) {
        if (insideThink) {
            emitReasoning(text, sink);
        } else {
            emitContent(text, sink);
        }
    }

    private int findPartialTagStart(String text, String tag) {
        int maxPartial = Math.min(text.length(), tag.length() - 1);
        for (int len = maxPartial; len > 0; len--) {
            String suffix = text.substring(text.length() - len);
            if (tag.startsWith(suffix)) {
                return text.length() - len;
            }
        }
        return -1;
    }

    private void emitContent(String text, FluxSink<ChatEvent> sink) {
        if (!text.isEmpty()) {
            contentBuffer.append(text);
            ChatEvent event = ChatEvent.content(text);
            sink.next(event);
        }
    }

    private void emitReasoning(String text, FluxSink<ChatEvent> sink) {
        if (!text.isEmpty()) {
            thinkBuffer.append(text);
            ChatEvent event = ChatEvent.reasoning(text);
            sink.next(event);
        }
    }

    String getAccumulatedContent() {
        return contentBuffer.toString();
    }

    String getAccumulatedReasoning() {
        return thinkBuffer.toString();
    }
}
