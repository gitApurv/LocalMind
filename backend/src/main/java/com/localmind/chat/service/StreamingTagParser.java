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
            if (!insideThink) {
                int thinkStart = text.indexOf(THINK_OPEN);
                if (thinkStart != -1) {
                    if (thinkStart > 0) {
                        emitContent(text.substring(0, thinkStart), sink);
                    }
                    insideThink = true;
                    text = text.substring(thinkStart + THINK_OPEN.length());
                } else {
                    int candidate = findPartialTagStart(text, THINK_OPEN);
                    if (candidate != -1) {
                        emitContent(text.substring(0, candidate), sink);
                        pending = text.substring(candidate);
                        text = "";
                    } else {
                        emitContent(text, sink);
                        text = "";
                    }
                }
            } else {
                int thinkEnd = text.indexOf(THINK_CLOSE);
                if (thinkEnd != -1) {
                    if (thinkEnd > 0) {
                        emitReasoning(text.substring(0, thinkEnd), sink);
                    }
                    insideThink = false;
                    text = text.substring(thinkEnd + THINK_CLOSE.length());
                } else {
                    int candidate = findPartialTagStart(text, THINK_CLOSE);
                    if (candidate != -1) {
                        emitReasoning(text.substring(0, candidate), sink);
                        pending = text.substring(candidate);
                        text = "";
                    } else {
                        emitReasoning(text, sink);
                        text = "";
                    }
                }
            }
        }
    }

    synchronized void flush(FluxSink<ChatEvent> sink) {
        if (!pending.isEmpty()) {
            if (insideThink) {
                emitReasoning(pending, sink);
            } else {
                emitContent(pending, sink);
            }
            pending = "";
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
            sink.next(ChatEvent.content(text));
        }
    }

    private void emitReasoning(String text, FluxSink<ChatEvent> sink) {
        if (!text.isEmpty()) {
            thinkBuffer.append(text);
            sink.next(ChatEvent.reasoning(text));
        }
    }

    String getAccumulatedContent() {
        return contentBuffer.toString();
    }

    String getAccumulatedReasoning() {
        return thinkBuffer.toString();
    }
}
