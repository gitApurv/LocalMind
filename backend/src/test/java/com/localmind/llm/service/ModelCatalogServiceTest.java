package com.localmind.llm.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.localmind.llm.config.LlmProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ModelCatalogServiceTest {

    private final ModelCatalogService service = new ModelCatalogService(RestClient.create(), LlmProperties.defaults(),
            new ModelProfileRegistry());

    @Test
    @DisplayName("isToolCapable prefers Ollama capabilities and falls back to model profiles")
    void isToolCapableEvaluation() {
        assertTrue(service.isToolCapable("custom-model", List.of("completion", "tools")));
        assertFalse(service.isToolCapable("gemma3:1b", List.of("completion")));
        assertFalse(service.isToolCapable("gemma3:1b", List.of()));
        assertTrue(service.isToolCapable("qwen2.5:7b", List.of()));
        assertFalse(service.isToolCapable("deepseek-r1:8b", List.of()));
        assertFalse(service.isToolCapable(null, null));
    }
}
