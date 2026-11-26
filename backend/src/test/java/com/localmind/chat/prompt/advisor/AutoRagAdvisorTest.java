package com.localmind.chat.prompt.advisor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.chat.prompt.token.CharacterTokenCounter;
import com.localmind.chat.prompt.token.TokenCounter;
import com.localmind.support.RecordingVectorStore;
import com.localmind.support.TestProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

/**
 * Unit tests for {@link AutoRagAdvisor}: retrieval skipped without ready attachments, reference excerpts injected into
 * the user message, and the unchanged request when the attachment lookup fails.
 */
class AutoRagAdvisorTest {

    private final RecordingVectorStore vectorStore = new RecordingVectorStore();
    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final TokenCounter tokenCounter = new CharacterTokenCounter();
    private final AutoRagAdvisor advisor = new AutoRagAdvisor(vectorStore, attachmentRepository, tokenCounter,
            TestProperties.promptDefaults());

    @Test
    void skipsRetrievalIfConversationHasNoReadyAttachments() {
        when(attachmentRepository.existsByConversationIdAndStatus("c1", Attachment.Status.READY)).thenReturn(false);

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("What is in the document?")))
                .context(Map.of("conversationId", "c1", "userId", "u1")).build();

        AdvisorChain chain = mock(AdvisorChain.class);
        ChatClientRequest result = advisor.before(request, chain);

        assertEquals(0, vectorStore.searches().size());
        assertEquals(request, result);
    }

    @Test
    void injectsReferenceExcerptsWhenAttachmentsExist() {
        when(attachmentRepository.existsByConversationIdAndStatus("c1", Attachment.Status.READY)).thenReturn(true);
        vectorStore.returnOnSearch(List
                .of(new Document("Report content summary", Map.of("fileName", "report.pdf", "type", "DOC_CHUNK"))));

        ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("Summarize report")))
                .context(Map.of("conversationId", "c1", "userId", "u1")).build();

        AdvisorChain chain = mock(AdvisorChain.class);
        ChatClientRequest result = advisor.before(request, chain);

        String userText = result.prompt().getUserMessage().getText();
        assertTrue(userText.contains("<reference source=\"report.pdf\">"));
        assertTrue(userText.contains("Report content summary"));
        assertTrue(userText.contains("Summarize report"));
    }

    @Test
    void returnsRequestUnchangedWhenAttachmentLookupFails() {
        when(attachmentRepository.existsByConversationIdAndStatus(any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt("What does the spec say?"))
                .context(Map.of("conversationId", "c1", "userId", "u1")).build();

        assertSame(request, advisor.before(request, mock(AdvisorChain.class)));
    }
}
