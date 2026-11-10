package com.localmind.common.config;

import com.localmind.common.ai.VectorMetadata;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds keyword payload indexes for the fields every multi-tenant search filters on. The collection itself is created by
 * Spring AI ({@code spring.ai.vectorstore.qdrant.initialize-schema}) with the embedding model's dimension.
 */
@Slf4j
@Configuration
public class QdrantConfig {

    private static final List<String> KEYWORD_INDEXED_FIELDS = List.of(VectorMetadata.USER_ID,
            VectorMetadata.CONVERSATION_ID, VectorMetadata.TYPE, VectorMetadata.MEMORY_ID);

    @Bean
    public ApplicationRunner qdrantIndexInitializer(QdrantClient qdrantClient, QdrantProperties properties) {
        return args -> KEYWORD_INDEXED_FIELDS
                .forEach(field -> ensureKeywordIndex(qdrantClient, properties.collectionName(), field));
    }

    private void ensureKeywordIndex(QdrantClient client, String collection, String fieldName) {
        try {
            client.createPayloadIndexAsync(collection, fieldName, Collections.PayloadSchemaType.Keyword, null, null,
                    null, null).get();
            log.debug("Ensured Qdrant payload keyword index for '{}'", fieldName);
        } catch (Exception e) {
            log.debug("Index for field '{}' may already exist or Qdrant is unavailable: {}", fieldName,
                    e.getMessage());
        }
    }
}
