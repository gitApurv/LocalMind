package com.localmind.llm.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.localmind.llm.config.LlmProperties;
import com.localmind.llm.dto.ModelInfo;
import com.localmind.llm.dto.ModelResponse;
import com.localmind.llm.model.ModelProfile;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Fetches models and their capabilities from Ollama and caches them. The list is loaded at startup (and validated there
 * when {@code localmind.llm.validate-models-on-startup} is on), then reloaded on demand when it is empty or older than
 * {@link #CACHE_TTL}, so models pulled later appear without a restart. Reloads during requests never throw and are
 * attempted at most once per {@link #RETRY_INTERVAL} while Ollama is unreachable. There are no hardcoded model lists.
 */
@Slf4j
@Service
public class ModelCatalogService {

    private static final String TOOLS_CAPABILITY = "tools";
    private static final String COMPLETION_CAPABILITY = "completion";
    private static final String LATEST_TAG = ":latest";
    static final Duration CACHE_TTL = Duration.ofMinutes(5);
    static final Duration RETRY_INTERVAL = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final LlmProperties llmProperties;
    private final ModelProfileRegistry modelProfileRegistry;
    private volatile CachedModels cachedModels;
    private volatile Instant lastAttempt;

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

    /**
     * Startup load. With validation on, an unreachable Ollama or a missing default model throws, which fails startup
     * because it runs from {@code @PostConstruct}.
     */
    public synchronized void refreshCatalog() {
        load(llmProperties.validateModelsOnStartup());
    }

    /** Request-time reload of an empty or stale catalogue. Never throws; failures keep the previous list. */
    synchronized void refreshIfStale() {
        Instant now = Instant.now();
        CachedModels cache = cachedModels;
        boolean fresh = cache != null && !cache.models().isEmpty() && cache.fetchedAt().plus(CACHE_TTL).isAfter(now);
        boolean retriedRecently = lastAttempt != null && lastAttempt.plus(RETRY_INTERVAL).isAfter(now);
        if (fresh || retriedRecently) {
            return;
        }
        try {
            load(false);
        } catch (RuntimeException e) {
            log.warn("Could not refresh the model catalogue: {}", e.getMessage());
        }
    }

    private void load(boolean validate) {
        lastAttempt = Instant.now();
        OllamaTagsResponse response;
        try {
            String tagsUrl = llmProperties.ollamaBaseUrl() + "/api/tags";
            response = restClient.get().uri(tagsUrl).accept(MediaType.APPLICATION_JSON).retrieve()
                    .body(OllamaTagsResponse.class);
        } catch (Exception e) {
            if (validate) {
                String message = "Failed to connect to Ollama at '" + llmProperties.ollamaBaseUrl()
                        + "'. LocalMind requires Ollama to be running on startup with models '"
                        + llmProperties.defaultModel() + "' and '" + llmProperties.defaultEmbeddingModel() + "'.";
                throw new IllegalStateException(message, e);
            }
            log.warn("Failed to fetch model tags from Ollama at '{}': {}", llmProperties.ollamaBaseUrl(),
                    e.getMessage());
            if (cachedModels == null) {
                cachedModels = new CachedModels(List.of(), llmProperties.defaultModel(), Instant.now());
            }
            return;
        }

        List<OllamaTagModel> rawModels = response != null && response.models() != null ? response.models() : List.of();
        if (validate) {
            validateRequiredModels(rawModels);
        }
        List<OllamaTagModel> chatModels = rawModels.stream().filter(this::isChatCapable).toList();
        chatModels.forEach(this::registerProfiles);
        List<ModelInfo> modelInfoList = chatModels.stream().map(this::toModelInfo).toList();
        String defaultModel = resolveDefaultModel(modelInfoList);
        cachedModels = new CachedModels(List.copyOf(modelInfoList), defaultModel, Instant.now());
        log.info("Loaded model catalogue from Ollama with {} models. Default model: '{}'", modelInfoList.size(),
                defaultModel);
    }

    private void registerProfiles(OllamaTagModel tag) {
        boolean toolSupport = isToolCapable(tag.capabilities());
        Integer contextLength = tag.details() != null ? tag.details().contextLength() : null;
        modelProfileRegistry.registerProfile(ModelProfile.fromOllama(tag.name(), contextLength, toolSupport));
        if (tag.model() != null && !tag.model().equals(tag.name())) {
            modelProfileRegistry.registerProfile(ModelProfile.fromOllama(tag.model(), contextLength, toolSupport));
        }
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
        refreshIfStale();
        CachedModels cache = cachedModels;
        if (cache == null) {
            return new ModelResponse(List.of(), llmProperties.defaultModel());
        }
        return new ModelResponse(cache.models(), cache.defaultModel());
    }

    /**
     * Whether tool calling should be offered to {@code modelName}. A name without a tag matches its {@code :latest}
     * tag, as in Ollama; other tags of the same model are different models and never match.
     */
    public boolean supportsTools(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        refreshIfStale();
        CachedModels cache = cachedModels;
        if (cache != null) {
            String trimmed = modelName.trim();
            for (ModelInfo info : cache.models()) {
                if (matchesName(info.name(), trimmed) || matchesName(info.model(), trimmed)) {
                    return info.supportsTools();
                }
            }
        }
        return modelProfileRegistry.getProfile(modelName).toolSupported();
    }

    private static boolean matchesName(String candidate, String requested) {
        return candidate != null
                && (candidate.equalsIgnoreCase(requested) || candidate.equalsIgnoreCase(requested + LATEST_TAG));
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
        boolean toolSupport = isToolCapable(tag.capabilities());
        return new ModelInfo(tag.name(), tag.model(), tag.size(), tag.modifiedAt(), toolSupport);
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

    private record CachedModels(List<ModelInfo> models, String defaultModel, Instant fetchedAt) {
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
