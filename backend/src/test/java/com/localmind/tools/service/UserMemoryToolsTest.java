package com.localmind.tools.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.localmind.memory.entity.UserMemory;
import com.localmind.memory.repository.UserMemoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class UserMemoryToolsTest {

    @Mock
    private UserMemoryRepository userMemoryRepository;

    private TestVectorStore vectorStore;
    private UserMemoryTools userMemoryTools;

    static class TestVectorStore implements VectorStore {
        final List<Document> addedDocuments = new ArrayList<>();
        List<Document> searchResults = new ArrayList<>();

        @Override
        public void add(List<Document> documents) {
            addedDocuments.addAll(documents);
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return searchResults;
        }
    }

    @BeforeEach
    void setUp() {
        vectorStore = new TestVectorStore();
        userMemoryTools = new UserMemoryTools(userMemoryRepository, vectorStore);
    }

    @Test
    @DisplayName("saveUserMemory() should persist fact in repository and index into vector store")
    void saveUserMemorySuccess() {
        when(userMemoryRepository.save(any(UserMemory.class))).thenAnswer(invocation -> {
            UserMemory memory = invocation.getArgument(0);
            return UserMemory.builder().id("mem-123").userId(memory.getUserId()).memoryFact(memory.getMemoryFact())
                    .build();
        });

        ToolContext context = new ToolContext(Map.of("userId", "user-42"));
        String result = userMemoryTools.saveUserMemory("User prefers Python over JavaScript", context);

        assertNotNull(result);
        assertTrue(result.contains("Memory saved successfully"));

        ArgumentCaptor<UserMemory> captor = ArgumentCaptor.forClass(UserMemory.class);
        verify(userMemoryRepository).save(captor.capture());
        assertEquals("user-42", captor.getValue().getUserId());
        assertEquals("User prefers Python over JavaScript", captor.getValue().getMemoryFact());

        assertEquals(1, vectorStore.addedDocuments.size());
        Document doc = vectorStore.addedDocuments.get(0);
        assertEquals("User prefers Python over JavaScript", doc.getText());
        assertEquals("user-42", doc.getMetadata().get("userId"));
        assertEquals("USER_MEMORY", doc.getMetadata().get("type"));
    }

    @Test
    @DisplayName("saveUserMemory() when userId is missing from ToolContext should return security error")
    void saveUserMemoryMissingUserId() {
        ToolContext emptyContext = new ToolContext(Map.of());
        String result = userMemoryTools.saveUserMemory("Some fact", emptyContext);

        assertTrue(result.toLowerCase().contains("missing") || result.toLowerCase().contains("security"));
        verifyNoInteractions(userMemoryRepository);
        assertTrue(vectorStore.addedDocuments.isEmpty());

        String nullContextResult = userMemoryTools.saveUserMemory("Some fact", null);
        assertTrue(nullContextResult.toLowerCase().contains("missing")
                || nullContextResult.toLowerCase().contains("security"));
    }

    @Test
    @DisplayName("saveUserMemory() with blank fact should return error")
    void saveUserMemoryBlankFact() {
        ToolContext context = new ToolContext(Map.of("userId", "user-42"));
        String result = userMemoryTools.saveUserMemory("   ", context);

        assertTrue(result.toLowerCase().contains("error") || result.toLowerCase().contains("blank"));
        verifyNoInteractions(userMemoryRepository);
        assertTrue(vectorStore.addedDocuments.isEmpty());
    }

    @Test
    @DisplayName("FunctionCallback for UserMemoryTools should extract ToolContext and save memory")
    void functionCallbackExecution() {
        when(userMemoryRepository.save(any(UserMemory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var callback = userMemoryTools.saveToolCallback();
        assertNotNull(callback);
        assertEquals("saveUserMemory", callback.getToolDefinition().name());

        ToolContext context = new ToolContext(Map.of("userId", "user-callback"));
        String response = callback.call("{\"fact\": \"Birthday is September 12\"}", context);

        assertNotNull(response);
        assertTrue(response.contains("Memory saved successfully"));
        assertEquals(1, vectorStore.addedDocuments.size());
        assertEquals("user-callback", vectorStore.addedDocuments.get(0).getMetadata().get("userId"));
    }

    @Test
    @DisplayName("recallUserMemories() should return semantic vector search matches when available")
    void recallUserMemoriesSemanticSearchSuccess() {
        vectorStore.searchResults = List.of(
                new Document("User prefers dark mode", Map.of("userId", "user-42", "type", "USER_MEMORY")),
                new Document("User is a senior software engineer", Map.of("userId", "user-42", "type", "USER_MEMORY")));

        ToolContext context = new ToolContext(Map.of("userId", "user-42"));
        String result = userMemoryTools.recallUserMemories("What does user prefer?", context);

        assertNotNull(result);
        assertTrue(result.contains("User prefers dark mode"));
        assertTrue(result.contains("User is a senior software engineer"));
        verifyNoInteractions(userMemoryRepository);
    }

    @Test
    @DisplayName("recallUserMemories() should fall back to relational database when vector search yields no matches")
    void recallUserMemoriesFallbackToDatabase() {
        vectorStore.searchResults = List.of();
        when(userMemoryRepository.findByUserIdOrderByCreatedAtDesc("user-42")).thenReturn(List.of(
                UserMemory.builder().id("mem-1").userId("user-42").memoryFact("User has a pet dog named Max").build(),
                UserMemory.builder().id("mem-2").userId("user-42").memoryFact("Prefers concise responses").build()));

        ToolContext context = new ToolContext(Map.of("userId", "user-42"));
        String result = userMemoryTools.recallUserMemories("pets", context);

        assertNotNull(result);
        assertTrue(result.contains("User has a pet dog named Max"));
        assertTrue(result.contains("Prefers concise responses"));
        verify(userMemoryRepository).findByUserIdOrderByCreatedAtDesc("user-42");
    }

    @Test
    @DisplayName("recallUserMemories() with missing userId in ToolContext should return security error")
    void recallUserMemoriesMissingUserId() {
        ToolContext emptyContext = new ToolContext(Map.of());
        String result = userMemoryTools.recallUserMemories("anything", emptyContext);

        assertTrue(result.toLowerCase().contains("missing") || result.toLowerCase().contains("security"));
        verifyNoInteractions(userMemoryRepository);

        String nullResult = userMemoryTools.recallUserMemories("anything", null);
        assertTrue(nullResult.toLowerCase().contains("missing") || nullResult.toLowerCase().contains("security"));
    }

    @Test
    @DisplayName("FunctionCallback for recallUserMemories should parse request and execute")
    void recallFunctionCallbackExecution() {
        when(userMemoryRepository.findByUserIdOrderByCreatedAtDesc("user-callback"))
                .thenReturn(List.of(UserMemory.builder().id("mem-3")
                        .userId("user-callback").memoryFact("Birthday is in September").build()));

        var callback = userMemoryTools.recallToolCallback();
        assertNotNull(callback);
        assertEquals("recallUserMemories", callback.getToolDefinition().name());

        ToolContext context = new ToolContext(Map.of("userId", "user-callback"));
        String response = callback.call("{\"query\": \"birthday\"}", context);

        assertNotNull(response);
        assertTrue(response.contains("Birthday is in September"));
    }
}
