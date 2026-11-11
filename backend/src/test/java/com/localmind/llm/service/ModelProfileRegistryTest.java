package com.localmind.llm.service;

import static org.junit.jupiter.api.Assertions.*;

import com.localmind.llm.model.ModelProfile;
import org.junit.jupiter.api.Test;

class ModelProfileRegistryTest {
    private final ModelProfileRegistry registry = new ModelProfileRegistry();

    @Test
    void testDefaultProfile() {
        ModelProfile profile = registry.getProfile("gemma3:1b");
        assertNotNull(profile);
        assertEquals(8192, profile.numCtx());
        assertFalse(profile.toolSupported());
    }

    @Test
    void testQwenProfile() {
        ModelProfile profile = registry.getProfile("qwen2.5:7b");
        assertNotNull(profile);
        assertTrue(profile.toolSupported());
    }

    @Test
    void testDeepSeekR1Profile() {
        ModelProfile profile = registry.getProfile("deepseek-r1:1.5b");
        assertNotNull(profile);
        assertFalse(profile.toolSupported());
    }

    @Test
    void infersToolSupportForUnknownModelsFromFamilyName() {
        assertFalse(registry.getProfile("gemma:2b").toolSupported());
        assertFalse(registry.getProfile("nomic-embed-text").toolSupported());
        assertFalse(registry.getProfile("deepseek-r1:8b").toolSupported());
        assertTrue(registry.getProfile("llama3.1:8b").toolSupported());
        assertTrue(registry.getProfile("gemma3-tools:latest").toolSupported());
    }

    @Test
    void blankModelNameFallsBackToDefaultProfile() {
        assertEquals("gemma3:1b", registry.getProfile(" ").modelName());
    }
}
