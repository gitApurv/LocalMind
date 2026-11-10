package com.localmind.common.security;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser origins allowed to call the API with credentials.
 */
@ConfigurationProperties(prefix = "localmind.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = List.copyOf(
                Objects.requireNonNullElse(allowedOrigins, List.of("http://localhost:3000", "http://127.0.0.1:3000")));
    }
}
