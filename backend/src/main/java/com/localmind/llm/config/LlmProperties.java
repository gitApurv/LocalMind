package com.localmind.llm.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Model configuration properties for Ollama integration.
 */
@ConfigurationProperties(prefix = "localmind.llm")
public record LlmProperties(String defaultModel, String defaultEmbeddingModel, String ollamaBaseUrl,
        boolean validateModelsOnStartup) {

    private static final String DEFAULT_CHAT_MODEL = "gemma3:1b";
    private static final String DEFAULT_EMBEDDING_MODEL = "nomic-embed-text";
    private static final String FALLBACK_OLLAMA_BASE_URL = "http://127.0.0.1:11434";

    public LlmProperties {
        defaultModel = isBlank(defaultModel) ? DEFAULT_CHAT_MODEL : defaultModel.trim();
        defaultEmbeddingModel = isBlank(defaultEmbeddingModel) ? DEFAULT_EMBEDDING_MODEL : defaultEmbeddingModel.trim();
        ollamaBaseUrl = stripTrailingSlash(Objects.requireNonNullElse(ollamaBaseUrl, FALLBACK_OLLAMA_BASE_URL));
    }

    public static LlmProperties defaults() {
        return new LlmProperties(null, null, null, false);
    }

    /**
     * Returns the first non-blank candidate (trimmed), or the configured default model.
     */
    public String resolveModel(String... candidates) {
        for (String candidate : candidates) {
            if (!isBlank(candidate)) {
                return candidate.trim();
            }
        }
        return defaultModel;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
