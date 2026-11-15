package com.localmind.llm.service;

import static org.junit.jupiter.api.Assertions.*;

import com.localmind.llm.model.ModelProfile;
import org.junit.jupiter.api.Test;

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
    void blankModelNameFallsBackToRegisteredOrGeneric() {
        ModelProfile profile = registry.getProfile(" ");
        assertNotNull(profile);
    }
}
