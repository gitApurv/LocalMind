package com.localmind.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QdrantProperties}: default and trimmed collection names.
 */
class QdrantPropertiesTest {

    @Test
    @DisplayName("Missing or blank collection names should fall back to the default")
    void missingOrBlankUsesDefault() {
        assertEquals("localmind_vectors", new QdrantProperties(null).collectionName());
        assertEquals("localmind_vectors", new QdrantProperties(" ").collectionName());
    }

    @Test
    @DisplayName("A configured collection name should be kept, trimmed")
    void configuredNameIsKept() {
        assertEquals("custom", new QdrantProperties(" custom ").collectionName());
    }
}
