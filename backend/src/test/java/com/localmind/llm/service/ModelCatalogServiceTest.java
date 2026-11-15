package com.localmind.llm.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @DisplayName("isToolCapable evaluates tool capability directly from Ollama capabilities")
    void isToolCapableEvaluation() {
        assertTrue(service.isToolCapable(List.of("completion", "tools")));
        assertFalse(service.isToolCapable(List.of("completion")));
        assertFalse(service.isToolCapable(List.of()));
        assertFalse(service.isToolCapable(null));
    }

    @Test
    @DisplayName("refreshCatalog throws IllegalStateException when validation is enabled and Ollama is unreachable")
    void validationThrowsWhenOllamaUnreachable() {
        LlmProperties validatingProps = new LlmProperties("gemma3:1b", "nomic-embed-text", "http://127.0.0.1:59999",
                true);
        ModelCatalogService validatingService = new ModelCatalogService(RestClient.create(), validatingProps,
                new ModelProfileRegistry());

        assertThrows(IllegalStateException.class, validatingService::refreshCatalog);
    }

    @Test
    @DisplayName("validateRequiredModels throws IllegalStateException when default chat model is missing")
    void validationThrowsWhenChatModelMissing() {
        LlmProperties props = new LlmProperties("missing-chat:1b", "nomic-embed-text", "http://127.0.0.1:11434", true);
        ModelCatalogService svc = new ModelCatalogService(RestClient.create(), props, new ModelProfileRegistry());

        List<ModelCatalogService.OllamaTagModel> installed = List.of(new ModelCatalogService.OllamaTagModel(
                "nomic-embed-text:latest", "nomic-embed-text:latest", 100L, "2026-01-01", null, List.of("embedding")));

        assertThrows(IllegalStateException.class, () -> svc.validateRequiredModels(installed));
    }

    @Test
    @DisplayName("validateRequiredModels throws IllegalStateException when default embedding model is missing")
    void validationThrowsWhenEmbeddingModelMissing() {
        LlmProperties props = new LlmProperties("gemma3:1b", "missing-embed", "http://127.0.0.1:11434", true);
        ModelCatalogService svc = new ModelCatalogService(RestClient.create(), props, new ModelProfileRegistry());

        List<ModelCatalogService.OllamaTagModel> installed = List.of(new ModelCatalogService.OllamaTagModel("gemma3:1b",
                "gemma3:1b", 100L, "2026-01-01", null, List.of("completion")));

        assertThrows(IllegalStateException.class, () -> svc.validateRequiredModels(installed));
    }
}
