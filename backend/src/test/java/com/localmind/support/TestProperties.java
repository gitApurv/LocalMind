package com.localmind.support;

import com.localmind.attachment.config.FileStorageProperties;
import com.localmind.chat.config.PromptProperties;
import com.localmind.llm.config.LlmProperties;

/**
 * Test support: properties records with every default applied, for unit tests that do not bind configuration.
 */
public final class TestProperties {

    private TestProperties() {
    }

    public static LlmProperties llmDefaults() {
        return new LlmProperties(null, null, null, false);
    }

    public static PromptProperties promptDefaults() {
        return new PromptProperties(null, null, null, null);
    }

    public static FileStorageProperties fileStorage(String uploadDir) {
        return new FileStorageProperties(uploadDir, null, null);
    }
}
