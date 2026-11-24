package com.localmind.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CorsProperties}: default local origins for missing or blank values, and trimming with blank
 * entries dropped.
 */
class CorsPropertiesTest {

    private static final List<String> DEFAULT_ORIGINS = List.of("http://localhost:3000", "http://127.0.0.1:3000");

    @Test
    @DisplayName("Missing origins should fall back to the local frontend origins")
    void missingOriginsUseDefaults() {
        assertEquals(DEFAULT_ORIGINS, new CorsProperties(null).allowedOrigins());
    }

    @Test
    @DisplayName("Only blank origins (empty environment variable) should fall back to the defaults")
    void blankOriginsUseDefaults() {
        assertEquals(DEFAULT_ORIGINS, new CorsProperties(List.of("")).allowedOrigins());
        assertEquals(DEFAULT_ORIGINS, new CorsProperties(List.of()).allowedOrigins());
    }

    @Test
    @DisplayName("Blank entries should be dropped and the others trimmed")
    void blankEntriesAreDropped() {
        CorsProperties properties = new CorsProperties(Arrays.asList(" https://app.example.com ", "", null, " "));

        assertEquals(List.of("https://app.example.com"), properties.allowedOrigins());
    }
}
