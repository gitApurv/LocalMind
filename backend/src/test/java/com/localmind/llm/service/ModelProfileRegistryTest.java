package com.localmind.llm.service;

import static org.junit.jupiter.api.Assertions.*;

import com.localmind.llm.model.ModelProfile;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ModelProfileRegistry}: registering and retrieving profiles, tag-insensitive {@code :latest}
 * matching, and the generic profile for unknown or blank names.
 */
class ModelProfileRegistryTest {

    private final ModelProfileRegistry registry = new ModelProfileRegistry();

    @Test
    void registersAndRetrievesDynamicProfile() {
        ModelProfile profile = ModelProfile.fromOllama("custom-model:latest", 16384, true);
        registry.registerProfile(profile);

        ModelProfile retrieved = registry.getProfile("custom-model:latest");
        assertNotNull(retrieved);
        assertEquals("custom-model:latest", retrieved.modelName());
        assertEquals(16384, retrieved.numCtx());
        assertTrue(retrieved.toolSupported());
    }

    @Test
    void matchesModelNameWithoutTag() {
        ModelProfile profile = ModelProfile.fromOllama("custom-model:latest", 32768, false);
        registry.registerProfile(profile);

        ModelProfile retrieved = registry.getProfile("custom-model");
        assertNotNull(retrieved);
        assertEquals("custom-model:latest", retrieved.modelName());
        assertFalse(retrieved.toolSupported());
    }

    @Test
    void unknownModelGetsGenericProfile() {
        ModelProfile profile = registry.getProfile("unknown-model");
        assertNotNull(profile);
        assertEquals("unknown-model", profile.modelName());
        assertEquals(8192, profile.numCtx());
        assertFalse(profile.toolSupported());
    }

    @Test
    void blankModelNameGetsGenericProfile() {
        registry.registerProfile(ModelProfile.fromOllama("big-model:latest", 32768, true));

        ModelProfile profile = registry.getProfile(" ");

        assertEquals("default", profile.modelName());
        assertEquals(8192, profile.numCtx());
        assertFalse(profile.toolSupported());
    }

    @Test
    void bareNameDoesNotMatchANonLatestTag() {
        registry.registerProfile(ModelProfile.fromOllama("qwen2.5:7b", 32768, true));

        ModelProfile profile = registry.getProfile("qwen2.5");

        assertEquals("qwen2.5", profile.modelName());
        assertFalse(profile.toolSupported());
    }

    @Test
    void latestTagMatchesProfileRegisteredUnderBareName() {
        registry.registerProfile(ModelProfile.fromOllama("mistral", 16384, true));

        ModelProfile profile = registry.getProfile("Mistral:latest");

        assertEquals("mistral", profile.modelName());
        assertTrue(profile.toolSupported());
    }
}
