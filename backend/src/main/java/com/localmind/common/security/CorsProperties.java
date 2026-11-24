package com.localmind.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings bound from {@code localmind.cors}: the browser origins allowed to call the API with credentials. Blank
 * entries are dropped (an empty environment variable binds as one blank origin), and an empty list falls back to the
 * local frontend origins.
 */
@ConfigurationProperties(prefix = "localmind.cors")
public record CorsProperties(List<String> allowedOrigins) {

    private static final List<String> DEFAULT_ORIGINS = List.of("http://localhost:3000", "http://127.0.0.1:3000");

    public CorsProperties {
        List<String> configured = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().filter(origin -> origin != null && !origin.isBlank()).map(String::trim)
                        .toList();
        allowedOrigins = configured.isEmpty() ? DEFAULT_ORIGINS : configured;
    }
}
