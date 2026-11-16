package com.localmind.conversation.service;

import java.util.regex.Pattern;

/**
 * Removes {@code <think>...</think>} reasoning blocks emitted by reasoning models (e.g. DeepSeek-R1) from one-shot
 * completions. Some models omit the opening tag or are cut off before the closing one; that reasoning is removed too.
 */
final class ThinkTags {

    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

    private ThinkTags() {
    }

    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";

    static String strip(String text) {
        String result = THINK_BLOCK.matcher(text).replaceAll("");
        int close = result.lastIndexOf(CLOSE);
        if (close >= 0) {
            result = result.substring(close + CLOSE.length());
        }
        int open = result.indexOf(OPEN);
        if (open >= 0) {
            result = result.substring(0, open);
        }
        return result.trim();
    }
}
