package com.localmind.chat.prompt.advisor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localmind.chat.prompt.token.CharacterTokenCounter;
import com.localmind.chat.prompt.token.TokenCounter;
import com.localmind.memory.repository.UserMemoryRepository;
import com.localmind.support.RecordingVectorStore;
import com.localmind.support.TestProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

/**
 * Unit tests for {@link MemoryRetrievalAdvisor}: retrieved memories appended to the system message and the unchanged
 * request when vector search fails.
 */
class MemoryRetrievalAdvisorTest {

    private final RecordingVectorStore vectorStore = new RecordingVectorStore();
    private final UserMemoryRepository memoryRepository = mock(UserMemoryRepository.class);
    private final TokenCounter tokenCounter = new CharacterTokenCounter();
    private final MemoryRetrievalAdvisor advisor = new MemoryRetrievalAdvisor(vectorStore, memoryRepository,
            tokenCounter, TestProperties.promptDefaults());

    @Test
    void augmentsSystemMessageWithRetrievedMemories() {
        vectorStore.returnOnSearch(List
                .of(new Document("User prefers dark mode", Map.of("type", "USER_MEMORY", "userId", "u1"))));

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new SystemMessage("Base rules"), new UserMessage("What theme do I like?"))))
                .context(Map.of("userId", "u1")).build();

        AdvisorChain chain = mock(AdvisorChain.class);
        ChatClientRequest result = advisor.before(request, chain);

        String systemText = result.prompt().getSystemMessage().getText();
        assertTrue(systemText.contains("Relevant User Memories:"));
        assertTrue(systemText.contains("User prefers dark mode"));
    }

    @Test
    void returnsRequestUnchangedWhenVectorSearchFails() {
        RecordingVectorStore failingStore = new RecordingVectorStore();
        failingStore.failOnSearch(new IllegalStateException("Qdrant unavailable"));
        MemoryRetrievalAdvisor failing = new MemoryRetrievalAdvisor(failingStore, memoryRepository, tokenCounter,
                TestProperties.promptDefaults());
        ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt("What do I like?"))
                .context(Map.of("userId", "u1")).build();

        assertSame(request, failing.before(request, mock(AdvisorChain.class)));
    }
}
