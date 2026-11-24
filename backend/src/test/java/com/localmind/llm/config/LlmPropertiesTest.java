package com.localmind.llm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Unit tests for {@link LlmProperties}: defaults for absent values, blank and trailing-slash base URL normalisation,
 * and validation staying on when the key is not bound.
 */
class LlmPropertiesTest {

    @Test
    void absentValuesGetDefaultsAndValidationIsOn() {
        LlmProperties properties = new LlmProperties(null, null, null, null);

        assertEquals("gemma3:1b", properties.defaultModel());
        assertEquals("nomic-embed-text", properties.defaultEmbeddingModel());
        assertEquals("http://127.0.0.1:11434", properties.ollamaBaseUrl());
        assertTrue(properties.validateModelsOnStartup());
    }

    @Test
    void blankOllamaBaseUrlFallsBackToDefault() {
        LlmProperties properties = new LlmProperties(null, null, "  ", false);

        assertEquals("http://127.0.0.1:11434", properties.ollamaBaseUrl());
        assertFalse(properties.validateModelsOnStartup());
    }

    @Test
    void baseUrlIsTrimmedWithoutTrailingSlash() {
        assertEquals("http://ollama:11434",
                new LlmProperties(null, null, " http://ollama:11434/ ", null).ollamaBaseUrl());
    }

    @Test
    void bindingWithoutTheKeyKeepsValidationOn() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("localmind.llm.default-model", "x")));

        LlmProperties properties = binder.bind("localmind.llm", LlmProperties.class).get();

        assertTrue(properties.validateModelsOnStartup());
    }
}
