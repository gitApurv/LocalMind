package com.localmind.tools.service;

import static org.junit.jupiter.api.Assertions.*;

import com.localmind.support.RecordingVectorStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;

/**
 * Unit tests for {@link DocumentSearchTools}: user- and conversation-scoped search with formatted excerpts, empty
 * results, missing context and blank queries, and invocation through its function callback.
 */
class DocumentSearchToolsTest {

    private RecordingVectorStore vectorStore;
    private DocumentSearchTools documentSearchTools;

    @BeforeEach
    void setUp() {
        vectorStore = new RecordingVectorStore();
        documentSearchTools = new DocumentSearchTools(vectorStore);
    }

    @Test
    @DisplayName("searchUploadedDocs() should filter by userId and conversationId and return formatted excerpts")
    void searchUploadedDocsSuccess() {
        Document doc1 = new Document("LocalMind runs Ollama locally without cloud dependencies.",
                Map.of("fileName", "architecture.pdf", "userId", "u-1", "conversationId", "c-1"));
        Document doc2 = new Document("Qdrant provides high performance vector search.",
                Map.of("fileName", "rag-design.md", "userId", "u-1", "conversationId", "c-1"));
        vectorStore.returnOnSearch(List.of(doc1, doc2));

        ToolContext context = new ToolContext(Map.of("userId", "u-1", "conversationId", "c-1"));
        String result = documentSearchTools.searchUploadedDocs("How does LocalMind work?", context);

        assertNotNull(result);
        assertTrue(result.contains("Found 2 relevant excerpt(s)"));
        assertTrue(result.contains("architecture.pdf"));
        assertTrue(result.contains("LocalMind runs Ollama locally"));
        assertTrue(result.contains("rag-design.md"));

        assertNotNull(vectorStore.lastSearch());
        assertEquals("How does LocalMind work?", vectorStore.lastSearch().getQuery());
        assertTrue(vectorStore.lastSearch().hasFilterExpression());
    }

    @Test
    @DisplayName("searchUploadedDocs() with no matching documents should return friendly message")
    void searchUploadedDocsEmptyResults() {
        vectorStore.returnOnSearch(List.of());

        ToolContext context = new ToolContext(Map.of("userId", "u-1", "conversationId", "c-1"));
        String result = documentSearchTools.searchUploadedDocs("quantum cryptography", context);

        assertNotNull(result);
        assertTrue(result.contains("No relevant information found"));
    }

    @Test
    @DisplayName("searchUploadedDocs() when userId or conversationId is missing should return error")
    void searchUploadedDocsMissingContext() {
        ToolContext missingConv = new ToolContext(Map.of("userId", "u-1"));
        String res1 = documentSearchTools.searchUploadedDocs("query", missingConv);
        assertTrue(res1.toLowerCase().contains("missing") || res1.toLowerCase().contains("security"));

        ToolContext missingUser = new ToolContext(Map.of("conversationId", "c-1"));
        String res2 = documentSearchTools.searchUploadedDocs("query", missingUser);
        assertTrue(res2.toLowerCase().contains("missing") || res2.toLowerCase().contains("security"));

        String res3 = documentSearchTools.searchUploadedDocs("query", null);
        assertTrue(res3.toLowerCase().contains("missing") || res3.toLowerCase().contains("security"));
    }

    @Test
    @DisplayName("searchUploadedDocs() with blank query should return error")
    void searchUploadedDocsBlankQuery() {
        ToolContext context = new ToolContext(Map.of("userId", "u-1", "conversationId", "c-1"));
        String result = documentSearchTools.searchUploadedDocs("   ", context);

        assertTrue(result.toLowerCase().contains("error") || result.toLowerCase().contains("blank"));
    }

    @Test
    @DisplayName("ToolCallback invocation for DocumentSearchTools should return search excerpts")
    void functionCallbackExecution() {
        Document doc = new Document("Kubernetes cluster setup instructions.",
                Map.of("fileName", "k8s.txt", "userId", "u-cb", "conversationId", "c-cb"));
        vectorStore.returnOnSearch(List.of(doc));

        var callback = documentSearchTools.toolCallback();
        assertNotNull(callback);
        assertEquals("searchUploadedDocs", callback.getToolDefinition().name());

        ToolContext context = new ToolContext(Map.of("userId", "u-cb", "conversationId", "c-cb"));
        String jsonPayload = "{\"query\": \"Kubernetes\"}";
        String response = callback.call(jsonPayload, context);

        assertNotNull(response);
        assertTrue(response.contains("Kubernetes cluster setup instructions"));
        assertTrue(response.contains("k8s.txt"));
    }
}
