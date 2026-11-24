package com.localmind.llm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ModelNames}: bare names matching their {@code :latest} tag both ways, other tags and blank
 * names never matching, and canonical names.
 */
class ModelNamesTest {

    @Test
    void bareNameMatchesItsLatestTagBothWays() {
        assertTrue(ModelNames.matches("llama3.2:latest", "llama3.2"));
        assertTrue(ModelNames.matches("llama3.2", "llama3.2:latest"));
        assertTrue(ModelNames.matches(" Llama3.2 ", "llama3.2"));
    }

    @Test
    void otherTagsNeverMatch() {
        assertFalse(ModelNames.matches("qwen2.5:7b", "qwen2.5"));
        assertFalse(ModelNames.matches("qwen2.5", "qwen2.5:7b"));
        assertFalse(ModelNames.matches("qwen2.5:7b", "qwen2.5:14b"));
    }

    @Test
    void nullOrBlankNeverMatches() {
        assertFalse(ModelNames.matches(null, "llama3.2"));
        assertFalse(ModelNames.matches("llama3.2", null));
        assertFalse(ModelNames.matches(" ", " "));
    }

    @Test
    void canonicalDropsLatestTagAndCase() {
        assertEquals("llama3.2", ModelNames.canonical(" Llama3.2:LATEST "));
        assertEquals("qwen2.5:7b", ModelNames.canonical("qwen2.5:7b"));
    }
}
