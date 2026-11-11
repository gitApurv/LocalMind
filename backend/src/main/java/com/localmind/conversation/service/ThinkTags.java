package com.localmind.conversation.service;

import java.util.regex.Pattern;

/**
 * Removes {@code <think>...</think>} reasoning blocks emitted by reasoning models (e.g. DeepSeek-R1) from one-shot
 * completions.
 */
final class ThinkTags {

    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

    private ThinkTags() {
    }

    static String strip(String text) {
        return THINK_BLOCK.matcher(text).replaceAll("").trim();
    }
}
