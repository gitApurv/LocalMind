package com.localmind.tools.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "localmind.tools.searxng")
public record SearxngProperties(String baseUrl) {

    public SearxngProperties {
        baseUrl = Objects.requireNonNullElse(baseUrl, "http://127.0.0.1:8081");
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
