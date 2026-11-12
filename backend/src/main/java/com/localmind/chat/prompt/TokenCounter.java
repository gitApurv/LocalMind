package com.localmind.chat.prompt;

public interface TokenCounter {
    int estimate(String text);
}
