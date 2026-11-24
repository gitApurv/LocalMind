package com.localmind.support;

import com.localmind.llm.service.ModelCatalogService;
import com.localmind.llm.service.OllamaVersionCheck;
import io.qdrant.client.QdrantClient;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Test support: replaces every bean that talks to Ollama or Qdrant with a Mockito mock, so a {@code @SpringBootTest}
 * context starts and runs without opening a connection to either service, whether or not they run on the machine.
 * Startup probes ({@link OllamaVersionCheck}, the {@link ModelCatalogService} preload, the Qdrant index runner) become
 * no-ops, and embeddings, similarity searches and completions return Mockito defaults unless a test stubs them. Put it
 * on every non-live {@code @SpringBootTest} class; the identical set of overrides lets those classes share one cached
 * context.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@MockitoBean(types = {QdrantClient.class, VectorStore.class, EmbeddingModel.class, ChatModel.class,
        ModelCatalogService.class, OllamaVersionCheck.class})
public @interface OfflineAiStack {
}
