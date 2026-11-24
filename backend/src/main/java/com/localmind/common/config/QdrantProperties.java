package com.localmind.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings bound from {@code localmind.qdrant}: the Qdrant collection that gets the payload indexes. Mirrors {@code
 * spring.ai.vectorstore.qdrant.collection-name} in {@code application.yml} (same environment variable); Spring AI
 * creates the collection itself. A blank value falls back to the default like a missing one, so an empty environment
 * variable cannot name an empty collection.
 */
@ConfigurationProperties(prefix = "localmind.qdrant")
public record QdrantProperties(String collectionName) {

    private static final String DEFAULT_COLLECTION_NAME = "localmind_vectors";

    public QdrantProperties {
        collectionName = collectionName == null || collectionName.isBlank()
                ? DEFAULT_COLLECTION_NAME
                : collectionName.trim();
    }
}
