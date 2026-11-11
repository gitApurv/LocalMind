package com.localmind.llm.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Model defaults used by LocalMind. Values mirror {@code spring.ai.ollama.*} in application.yml so the Ollama settings
 * remain the single source of truth.
 */
@ConfigurationProperties(prefix = "localmind.llm")
public record LlmProperties(String defaultModel, String ollamaBaseUrl) {

    private static final String FALLBACK_MODEL = "gemma3:1b";
    private static final String FALLBACK_OLLAMA_BASE_URL = "http://127.0.0.1:11434";

    public LlmProperties {
        defaultModel = isBlank(defaultModel) ? FALLBACK_MODEL : defaultModel.trim();
        ollamaBaseUrl = stripTrailingSlash(Objects.requireNonNullElse(ollamaBaseUrl, FALLBACK_OLLAMA_BASE_URL));
    }

    public static LlmProperties defaults() {
        return new LlmProperties(null, null);
    }

    /**
     * Returns the first non-blank candidate (trimmed), or the default model.
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
