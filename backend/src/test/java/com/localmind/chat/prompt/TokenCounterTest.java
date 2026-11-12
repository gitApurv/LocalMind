package com.localmind.chat.prompt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TokenCounterTest {
    private final TokenCounter tokenCounter = new CharacterTokenCounter();

    @Test
    void estimateTokensNormalText() {
        assertEquals(0, tokenCounter.estimate(null));
        assertEquals(0, tokenCounter.estimate(""));
        assertEquals(1, tokenCounter.estimate("hi"));
        assertEquals(4, tokenCounter.estimate("hello world test"));
    }
}
