package com.localmind.chat.prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.chat.config.PromptProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class AutoRagAdvisorTest {

    static class TestVectorStore implements VectorStore {
        List<Document> simResults = List.of();
        int searchCount = 0;

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
            searchCount++;
            return simResults;
        }
    }

    private final TestVectorStore vectorStore = new TestVectorStore();
    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final TokenCounter tokenCounter = new CharacterTokenCounter();
    private final AutoRagAdvisor advisor = new AutoRagAdvisor(vectorStore, attachmentRepository, tokenCounter,
            PromptProperties.defaults());

    @Test
    void skipsRetrievalIfConversationHasNoReadyAttachments() {
        when(attachmentRepository.existsByConversationIdAndStatus("c1", Attachment.Status.READY)).thenReturn(false);

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("What is in the document?")))
                .context(Map.of("conversationId", "c1", "userId", "u1")).build();

        AdvisorChain chain = mock(AdvisorChain.class);
        ChatClientRequest result = advisor.before(request, chain);

        assertEquals(0, vectorStore.searchCount);
        assertEquals(request, result);
    }

    @Test
    void injectsReferenceExcerptsWhenAttachmentsExist() {
        when(attachmentRepository.existsByConversationIdAndStatus("c1", Attachment.Status.READY)).thenReturn(true);
        vectorStore.simResults = List
                .of(new Document("Report content summary", Map.of("fileName", "report.pdf", "type", "DOC_CHUNK")));

        ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("Summarize report")))
                .context(Map.of("conversationId", "c1", "userId", "u1")).build();

        AdvisorChain chain = mock(AdvisorChain.class);
        ChatClientRequest result = advisor.before(request, chain);

        String userText = result.prompt().getUserMessage().getText();
        assertTrue(userText.contains("<reference source=\"report.pdf\">"));
        assertTrue(userText.contains("Report content summary"));
        assertTrue(userText.contains("Summarize report"));
    }
}
