package com.localmind.tools.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings bound from {@code localmind.tools.searxng}: the SearXNG instance used by web search.
 *
 * @param baseUrl
 *            SearXNG instance used by web search; a blank value (e.g. {@code SEARXNG_BASE_URL=}) falls back to the
 *            local Docker default instead of producing relative request URLs
 */
@ConfigurationProperties(prefix = "localmind.tools.searxng")
public record SearxngProperties(String baseUrl) {

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8081";

    public SearxngProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
