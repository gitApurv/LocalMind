package com.localmind.chat.prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localmind.chat.config.PromptProperties;
import com.localmind.memory.repository.UserMemoryRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class MemoryRetrievalAdvisorTest {
    static class TestVectorStore implements VectorStore {
        List<Document> simResults = List.of();

        @Override
        public void add(List<Document> documents) {
        }
        @Override
        public void delete(List<String> idList) {
        }
        @Override
        public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
        }
        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return simResults;
        }
    }

    private final TestVectorStore vectorStore = new TestVectorStore();
    private final UserMemoryRepository memoryRepository = mock(UserMemoryRepository.class);
    private final TokenCounter tokenCounter = new CharacterTokenCounter();
    private final MemoryRetrievalAdvisor advisor = new MemoryRetrievalAdvisor(vectorStore, memoryRepository,
            tokenCounter, PromptProperties.defaults());

    @Test
    void augmentsSystemMessageWithRetrievedMemories() {
        vectorStore.simResults = List
                .of(new Document("User prefers dark mode", Map.of("type", "USER_MEMORY", "userId", "u1")));

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new SystemMessage("Base rules"), new UserMessage("What theme do I like?"))))
                .context(Map.of("userId", "u1")).build();

        AdvisorChain chain = mock(AdvisorChain.class);
        ChatClientRequest result = advisor.before(request, chain);

        String systemText = result.prompt().getSystemMessage().getText();
        assertTrue(systemText.contains("Relevant User Memories:"));
        assertTrue(systemText.contains("User prefers dark mode"));
    }
}
