package com.localmind.common.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Qdrant collection used for payload indexes. Mirrors {@code spring.ai.vectorstore.qdrant.collection-name} in
 * application.yml (same environment variable); Spring AI creates the collection itself.
 */
@ConfigurationProperties(prefix = "localmind.qdrant")
public record QdrantProperties(String collectionName) {

    public QdrantProperties {
        collectionName = Objects.requireNonNullElse(collectionName, "localmind_vectors");
    }
}
