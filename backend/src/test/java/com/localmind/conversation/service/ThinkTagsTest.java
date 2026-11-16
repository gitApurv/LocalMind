package com.localmind.conversation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThinkTagsTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {"<think>plan</think>Paris Trip | Paris Trip",
            "Paris <think>a</think>Trip<think>b</think> | Paris Trip",
            "plan only, no opening tag</think>Paris Trip | Paris Trip",
            "Paris Trip<think>cut off before closing | Paris Trip", "<think>never closed | ''",
            "Plain Title | Plain Title"})
    void stripsCompleteAndUnbalancedReasoning(String raw, String expected) {
        assertEquals(expected, ThinkTags.strip(raw));
    }
}
