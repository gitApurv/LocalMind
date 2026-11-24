package com.localmind.tools.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SearxngProperties}: default base URL for null or blank values and trimming of the trailing
 * slash.
 */
class SearxngPropertiesTest {

    @Test
    void nullOrBlankBaseUrlFallsBackToDefault() {
        assertEquals("http://127.0.0.1:8081", new SearxngProperties(null).baseUrl());
        assertEquals("http://127.0.0.1:8081", new SearxngProperties("  ").baseUrl());
    }

    @Test
    void baseUrlIsTrimmedWithoutTrailingSlash() {
        assertEquals("http://searxng:8080", new SearxngProperties(" http://searxng:8080/ ").baseUrl());
    }
}
