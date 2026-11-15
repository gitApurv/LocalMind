package com.localmind.llm.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.localmind.llm.config.LlmProperties;
import com.localmind.llm.dto.ModelInfo;
import com.localmind.llm.dto.ModelResponse;
import com.localmind.llm.model.ModelProfile;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Fetches models and their capabilities from Ollama on server startup and caches them for the complete lifecycle of the
 * application. Contains no hardcoded model registries or fallbacks.
 */
@Slf4j
@Service
public class ModelCatalogService {

    private static final String TOOLS_CAPABILITY = "tools";
    private static final String COMPLETION_CAPABILITY = "completion";

    private final RestClient restClient;
    private final LlmProperties llmProperties;
    private final ModelProfileRegistry modelProfileRegistry;
    private volatile CachedModels cachedModels;

    public ModelCatalogService(RestClient shortTimeoutRestClient, LlmProperties llmProperties,
            ModelProfileRegistry modelProfileRegistry) {
        this.restClient = shortTimeoutRestClient;
        this.llmProperties = llmProperties;
        this.modelProfileRegistry = modelProfileRegistry;
    }

    @PostConstruct
    public void init() {
        refreshCatalog();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (cachedModels == null || cachedModels.models().isEmpty()) {
            refreshCatalog();
        }
    }

    public synchronized void refreshCatalog() {
        OllamaTagsResponse response;
        try {
            response = restClient.get().uri(llmProperties.ollamaBaseUrl() + "/api/tags")
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(OllamaTagsResponse.class);
        } catch (Exception e) {
            if (llmProperties.validateModelsOnStartup()) {
                throw new IllegalStateException("Failed to connect to Ollama at '" + llmProperties.ollamaBaseUrl()
                        + "'. LocalMind requires Ollama to be running on startup with models '"
                        + llmProperties.defaultModel() + "' and '" + llmProperties.defaultEmbeddingModel() + "'.", e);
            }
            log.warn("Failed to fetch model tags from Ollama on startup at '{}': {}", llmProperties.ollamaBaseUrl(),
                    e.getMessage());
            if (this.cachedModels == null) {
                this.cachedModels = new CachedModels(List.of(), llmProperties.defaultModel());
            }
            return;
        }

        List<OllamaTagModel> rawModels = response != null && response.models() != null ? response.models() : List.of();

        if (llmProperties.validateModelsOnStartup()) {
            validateRequiredModels(rawModels);
        }

        List<OllamaTagModel> chatModels = rawModels.stream().filter(this::isChatCapable).toList();
        List<ModelInfo> modelInfoList = chatModels.stream().map(this::toModelInfo).toList();

        for (OllamaTagModel tag : chatModels) {
            boolean toolSupport = isToolCapable(tag.capabilities());
            Integer contextLength = tag.details() != null ? tag.details().contextLength() : null;
            modelProfileRegistry.registerProfile(ModelProfile.fromOllama(tag.name(), contextLength, toolSupport));
            if (tag.model() != null && !tag.model().equals(tag.name())) {
                modelProfileRegistry.registerProfile(ModelProfile.fromOllama(tag.model(), contextLength, toolSupport));
            }
        }

        String defaultModel = resolveDefaultModel(modelInfoList);
        this.cachedModels = new CachedModels(List.copyOf(modelInfoList), defaultModel);
        log.info("Initialized model catalog from Ollama with {} models. Default model: '{}'", modelInfoList.size(),
                defaultModel);
    }

    void validateRequiredModels(List<OllamaTagModel> installedModels) {
        String requiredChatModel = llmProperties.defaultModel();
        boolean chatModelFound = installedModels.stream().anyMatch(m -> matchesModel(m, requiredChatModel));
        if (!chatModelFound) {
            throw new IllegalStateException("Required default chat model '" + requiredChatModel
                    + "' was not found on the system. Please run 'ollama pull " + requiredChatModel
                    + "' before starting LocalMind.");
        }

        String requiredEmbeddingModel = llmProperties.defaultEmbeddingModel();
        boolean embeddingModelFound = installedModels.stream().anyMatch(m -> matchesModel(m, requiredEmbeddingModel));
        if (!embeddingModelFound) {
            throw new IllegalStateException("Required default embedding model '" + requiredEmbeddingModel
                    + "' was not found on the system. Please run 'ollama pull " + requiredEmbeddingModel
                    + "' before starting LocalMind.");
        }

        log.info("Verified required system models in Ollama: chat='{}', embedding='{}'", requiredChatModel,
                requiredEmbeddingModel);
    }

    private boolean matchesModel(OllamaTagModel tag, String targetModel) {
        if (tag == null || targetModel == null || targetModel.isBlank()) {
            return false;
        }
        String target = targetModel.trim();
        String targetWithLatest = target.contains(":") ? target : target + ":latest";
        String targetWithoutLatest = target.endsWith(":latest")
                ? target.substring(0, target.length() - ":latest".length())
                : target;

        return isMatch(tag.name(), target, targetWithLatest, targetWithoutLatest)
                || isMatch(tag.model(), target, targetWithLatest, targetWithoutLatest);
    }

    private boolean isMatch(String candidate, String target, String withLatest, String withoutLatest) {
        if (candidate == null) {
            return false;
        }
        return candidate.equalsIgnoreCase(target)
                || candidate.equalsIgnoreCase(withLatest)
                || candidate.equalsIgnoreCase(withoutLatest);
    }

    public ModelResponse listAvailableModels() {
        if (cachedModels == null || cachedModels.models().isEmpty()) {
            refreshCatalog();
        }
        return new ModelResponse(cachedModels.models(), cachedModels.defaultModel());
    }

    public String getDefaultModel() {
        if (cachedModels == null) {
            refreshCatalog();
        }
        return cachedModels != null ? cachedModels.defaultModel() : llmProperties.defaultModel();
    }

    /** Whether tool calling should be offered to {@code modelName}. */
    public boolean supportsTools(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        if (cachedModels == null) {
            refreshCatalog();
        }
        if (cachedModels != null) {
            String trimmed = modelName.trim();
            for (ModelInfo info : cachedModels.models()) {
                if (info.name().equalsIgnoreCase(trimmed) || info.model().equalsIgnoreCase(trimmed)
                        || info.name().startsWith(trimmed + ":") || trimmed.startsWith(info.name() + ":")) {
                    return info.supportsTools();
                }
            }
        }
        return modelProfileRegistry.getProfile(modelName).toolSupported();
    }

    public boolean isToolCapable(List<String> capabilities) {
        return capabilities != null && capabilities.contains(TOOLS_CAPABILITY);
    }

    private boolean isChatCapable(OllamaTagModel tag) {
        if (tag == null) {
            return false;
        }
        List<String> capabilities = tag.capabilities();
        if (capabilities == null || capabilities.isEmpty()) {
            return true;
        }
        return capabilities.contains(COMPLETION_CAPABILITY);
    }

    private ModelInfo toModelInfo(OllamaTagModel tag) {
        return new ModelInfo(tag.name(), tag.model(), tag.size(), tag.modifiedAt(),
                isToolCapable(tag.capabilities()));
    }

    private String resolveDefaultModel(List<ModelInfo> models) {
        String configured = llmProperties.defaultModel();
        if (configured != null && !configured.isBlank()) {
            String trimmed = configured.trim();
            for (ModelInfo info : models) {
                if (info.name().equalsIgnoreCase(trimmed) || info.model().equalsIgnoreCase(trimmed)) {
                    return info.name();
                }
            }
        }
        return models.isEmpty() ? configured : models.getFirst().name();
    }

    private record CachedModels(List<ModelInfo> models, String defaultModel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaTagsResponse(List<OllamaTagModel> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaTagModel(String name, String model, Long size, @JsonProperty("modified_at") String modifiedAt,
            ModelDetails details, List<String> capabilities) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelDetails(@JsonProperty("context_length") Integer contextLength, String family,
            @JsonProperty("parameter_size") String parameterSize) {
    }
}
