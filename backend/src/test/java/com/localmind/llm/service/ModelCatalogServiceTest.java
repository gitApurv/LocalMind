package com.localmind.llm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.localmind.llm.config.LlmProperties;
import com.localmind.llm.dto.ModelInfo;
import com.localmind.llm.dto.ModelResponse;
import com.localmind.support.TestProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ModelCatalogServiceTest {

    private final ModelCatalogService service = new ModelCatalogService(RestClient.create(),
            TestProperties.llmDefaults(),
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

    private static final String BASE_URL = "http://ollama.test";
    private static final String TAGS = """
            {"models": [
              {"name": "qwen2.5:7b", "model": "qwen2.5:7b", "size": 1, "modified_at": "2026-01-01",
               "details": {"context_length": 32768}, "capabilities": ["completion", "tools"]},
              {"name": "llama3.2:latest", "model": "llama3.2:latest", "size": 1, "modified_at": "2026-01-01",
               "details": {"context_length": 4096}, "capabilities": ["completion"]},
              {"name": "nomic-embed-text:latest", "model": "nomic-embed-text:latest", "size": 1,
               "modified_at": "2026-01-01", "capabilities": ["embedding"]}
            ]}""";

    @Test
    @DisplayName("refreshCatalog parses capabilities and context length from /api/tags and skips embedding models")
    void parsesTagsIntoModelsAndProfiles() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/api/tags")).andRespond(withSuccess(TAGS, MediaType.APPLICATION_JSON));
        ModelProfileRegistry registry = new ModelProfileRegistry();
        ModelCatalogService catalog = new ModelCatalogService(builder.build(),
                new LlmProperties("qwen2.5:7b", "nomic-embed-text", BASE_URL, true), registry);

        catalog.refreshCatalog();

        ModelResponse response = catalog.listAvailableModels();
        assertEquals(List.of("qwen2.5:7b", "llama3.2:latest"),
                response.models().stream().map(ModelInfo::name).toList());
        assertEquals("qwen2.5:7b", response.defaultModel());
        assertTrue(catalog.supportsTools("qwen2.5:7b"));
        assertFalse(catalog.supportsTools("llama3.2"));
        assertEquals(32768, registry.getProfile("qwen2.5:7b").numCtx());
        assertEquals(4096, registry.getProfile("llama3.2").numCtx());
    }

    @Test
    @DisplayName("supportsTools matches a bare name only to its :latest tag, never to another tag of the model")
    void bareNameDoesNotMatchOtherTags() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/api/tags")).andRespond(withSuccess(TAGS, MediaType.APPLICATION_JSON));
        ModelCatalogService catalog = new ModelCatalogService(builder.build(),
                new LlmProperties(null, null, BASE_URL, false), new ModelProfileRegistry());
        catalog.refreshCatalog();

        assertFalse(catalog.supportsTools("qwen2.5"));
    }

    @Test
    @DisplayName("A request-time reload never throws, even with validation on, and is not retried within 30 s")
    void requestTimeReloadNeverThrowsAndIsThrottled() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(BASE_URL + "/api/tags")).andRespond(withServerError());
        ModelCatalogService catalog = new ModelCatalogService(builder.build(),
                new LlmProperties("gemma3:1b", "nomic-embed-text", BASE_URL, true), new ModelProfileRegistry());

        ModelResponse first = catalog.listAvailableModels();
        ModelResponse second = catalog.listAvailableModels();

        server.verify();
        assertTrue(first.models().isEmpty());
        assertTrue(second.models().isEmpty());
        assertEquals("gemma3:1b", second.defaultModel());
    }
}
