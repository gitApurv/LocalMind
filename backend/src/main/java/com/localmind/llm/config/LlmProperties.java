package com.localmind.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings bound from {@code localmind.llm}: Ollama connection and default model selection. Blank values fall back to
 * {@code gemma3:1b}, {@code nomic-embed-text} and {@code http://127.0.0.1:11434}.
 *
 * @param validateModelsOnStartup
 *            fail startup when Ollama is unreachable, too old or missing a default model; {@code true} when absent
 *            because a misconfigured stack should be noticed at startup, not on the first chat request
 */
@ConfigurationProperties(prefix = "localmind.llm")
public record LlmProperties(String defaultModel, String defaultEmbeddingModel, String ollamaBaseUrl,
        Boolean validateModelsOnStartup) {

    private static final String DEFAULT_CHAT_MODEL = "gemma3:1b";
    private static final String DEFAULT_EMBEDDING_MODEL = "nomic-embed-text";
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://127.0.0.1:11434";

    public LlmProperties {
        defaultModel = isBlank(defaultModel) ? DEFAULT_CHAT_MODEL : defaultModel.trim();
        defaultEmbeddingModel = isBlank(defaultEmbeddingModel) ? DEFAULT_EMBEDDING_MODEL : defaultEmbeddingModel.trim();
        ollamaBaseUrl = stripTrailingSlash(isBlank(ollamaBaseUrl) ? DEFAULT_OLLAMA_BASE_URL : ollamaBaseUrl.trim());
        validateModelsOnStartup = validateModelsOnStartup == null || validateModelsOnStartup;
    }

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
