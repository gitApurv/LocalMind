package com.localmind.llm.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.localmind.llm.config.LlmProperties;
import com.localmind.llm.dto.ModelInfo;
import com.localmind.llm.dto.ModelResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Lists the models installed in Ollama and is the single source of truth for whether a model supports tool calling: the
 * capabilities Ollama reports win, the static {@link ModelProfileRegistry} is only a fallback. The model list is cached
 * briefly because the chat pipeline asks on every turn.
 */
@Slf4j
@Service
public class ModelCatalogService {

    private static final String TOOLS_CAPABILITY = "tools";
    private static final String DEFAULT_TAG = ":latest";
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final RestClient restClient;
    private final LlmProperties llmProperties;
    private final ModelProfileRegistry modelProfileRegistry;
    private volatile CachedTags cachedTags;

    public ModelCatalogService(RestClient shortTimeoutRestClient, LlmProperties llmProperties,
            ModelProfileRegistry modelProfileRegistry) {
        this.restClient = shortTimeoutRestClient;
        this.llmProperties = llmProperties;
        this.modelProfileRegistry = modelProfileRegistry;
    }

    public ModelResponse listAvailableModels() {
        List<OllamaTagModel> models = fetchTags();
        if (!models.isEmpty()) {
            return new ModelResponse(models.stream().map(this::toModelInfo).toList());
        }
        String defaultModel = llmProperties.defaultModel();
        return new ModelResponse(
                List.of(new ModelInfo(defaultModel, defaultModel, null, null, isToolCapable(defaultModel, null))));
    }

    /** Whether tool calling should be offered to {@code modelName}. */
    public boolean supportsTools(String modelName) {
        return isToolCapable(modelName, capabilitiesOf(modelName));
    }

    /**
     * Prefers the capabilities reported by Ollama; otherwise falls back to the static model profile.
     */
    public boolean isToolCapable(String modelName, List<String> capabilities) {
        if (capabilities != null && !capabilities.isEmpty()) {
            return capabilities.contains(TOOLS_CAPABILITY);
        }
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        return modelProfileRegistry.getProfile(modelName).toolSupported();
    }

    private List<String> capabilitiesOf(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        CachedTags tags = cachedTags;
        if (tags == null || tags.isExpired()) {
            fetchTags();
            tags = cachedTags;
        }
        if (tags == null) {
            return null;
        }
        String name = modelName.trim();
        List<String> capabilities = tags.capabilities().get(name);
        return capabilities != null || name.contains(":")
                ? capabilities
                : tags.capabilities().get(name + DEFAULT_TAG);
    }

    /** Fetches the installed models and refreshes the capability cache; empty when Ollama is unreachable. */
    private List<OllamaTagModel> fetchTags() {
        try {
            OllamaTagsResponse response = restClient.get().uri(llmProperties.ollamaBaseUrl() + "/api/tags")
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(OllamaTagsResponse.class);
            List<OllamaTagModel> models = response != null && response.models() != null ? response.models() : List.of();
            cachedTags = CachedTags.of(models);
            return models;
        } catch (Exception e) {
            log.warn("Failed to fetch model tags from Ollama at '{}': {}", llmProperties.ollamaBaseUrl(),
                    e.getMessage());
            return List.of();
        }
    }

    private ModelInfo toModelInfo(OllamaTagModel tag) {
        return new ModelInfo(tag.name(), tag.model(), tag.size(), tag.modifiedAt(),
                isToolCapable(tag.name(), tag.capabilities()));
    }

    private record CachedTags(Map<String, List<String>> capabilities, Instant fetchedAt) {

        static CachedTags of(List<OllamaTagModel> models) {
            Map<String, List<String>> capabilities = new HashMap<>();
            for (OllamaTagModel model : models) {
                List<String> modelCapabilities = model.capabilities() != null ? model.capabilities() : List.of();
                if (model.name() != null) {
                    capabilities.put(model.name(), modelCapabilities);
                }
                if (model.model() != null) {
                    capabilities.putIfAbsent(model.model(), modelCapabilities);
                }
            }
            return new CachedTags(Map.copyOf(capabilities), Instant.now());
        }

        boolean isExpired() {
            return fetchedAt.plus(CACHE_TTL).isBefore(Instant.now());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaTagsResponse(List<OllamaTagModel> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaTagModel(String name, String model, Long size, @JsonProperty("modified_at") String modifiedAt,
            List<String> capabilities) {
    }
}
