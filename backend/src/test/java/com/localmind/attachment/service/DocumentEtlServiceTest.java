package com.localmind.attachment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.attachment.storage.FileStorageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class DocumentEtlServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private FileStorageService fileStorageService;

    private TestVectorStore vectorStore;
    private DocumentEtlService documentEtlService;

    static class TestVectorStore implements VectorStore {
        final List<Document> addedDocuments = new ArrayList<>();

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
            return List.of();
        }
    }

    @BeforeEach
    void setUp() {
        vectorStore = new TestVectorStore();
        documentEtlService = new DocumentEtlService(attachmentRepository, fileStorageService, vectorStore);
    }

    @Test
    @DisplayName("processAttachment() should parse text, chunk, enrich metadata, and index in vector store")
    void processAttachmentSuccess() {
        String attachmentId = "att-1";
        Attachment attachment = Attachment.builder().id(attachmentId).conversationId("conv-1").userId("user-1")
                .fileName("test.txt").fileType("text/plain").fileSize(1024L).status(Attachment.Status.PROCESSING)
                .build();

        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(attachmentRepository.existsById(attachmentId)).thenReturn(true);
        String sampleText = "LocalMind is an open-source privacy-first conversational AI platform. "
                + "It uses Ollama for local model execution and Qdrant for RAG vector retrieval.";
        Resource mockResource = new ByteArrayResource(sampleText.getBytes(StandardCharsets.UTF_8));
        when(fileStorageService.loadAsResource("user-1", attachmentId, "test.txt")).thenReturn(mockResource);

        documentEtlService.processAttachment(attachmentId);

        List<Document> savedDocs = vectorStore.addedDocuments;
        assertFalse(savedDocs.isEmpty());
        for (Document doc : savedDocs) {
            assertEquals("user-1", doc.getMetadata().get("userId"));
            assertEquals("conv-1", doc.getMetadata().get("conversationId"));
            assertEquals(attachmentId, doc.getMetadata().get("attachmentId"));
            assertEquals("test.txt", doc.getMetadata().get("fileName"));
            assertEquals("DOC_CHUNK", doc.getMetadata().get("type"));
            assertNotNull(doc.getMetadata().get("chunkIndex"));
        }

        assertEquals(Attachment.Status.READY, attachment.getStatus());
        assertTrue(attachment.getChunkCount() > 0);
        assertNull(attachment.getErrorMessage());
        verify(attachmentRepository).save(attachment);
    }

    @Test
    @DisplayName("processAttachment() with corrupted or failing file should mark status FAILED")
    void processAttachmentCorruptedFile() throws Exception {
        String attachmentId = "att-fail";
        Attachment attachment = Attachment.builder().id(attachmentId).conversationId("conv-1").userId("user-1")
                .fileName("corrupted.bin").fileType("application/octet-stream").status(Attachment.Status.PROCESSING)
                .build();

        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(attachmentRepository.existsById(attachmentId)).thenReturn(true);

        Resource mockBrokenResource = mock(Resource.class);
        when(mockBrokenResource.getInputStream()).thenThrow(new IOException("Disk I/O failure or corrupt payload"));
        when(fileStorageService.loadAsResource("user-1", attachmentId, "corrupted.bin")).thenReturn(mockBrokenResource);

        assertDoesNotThrow(() -> documentEtlService.processAttachment(attachmentId));

        assertEquals(Attachment.Status.FAILED, attachment.getStatus());
        assertNotNull(attachment.getErrorMessage());
        verify(attachmentRepository).save(attachment);
        assertTrue(vectorStore.addedDocuments.isEmpty());
    }

    @Test
    @DisplayName("processAttachment() should not resurrect an attachment deleted while it was processed")
    void processAttachmentDeletedDuringProcessing() {
        String attachmentId = "att-deleted";
        Attachment attachment = Attachment.builder().id(attachmentId).conversationId("conv-1").userId("user-1")
                .fileName("gone.txt").status(Attachment.Status.PROCESSING).build();
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(attachmentRepository.existsById(attachmentId)).thenReturn(false);
        when(fileStorageService.loadAsResource(any(), any(), any()))
                .thenReturn(new ByteArrayResource("Some text content".getBytes(StandardCharsets.UTF_8)));

        documentEtlService.processAttachment(attachmentId);

        verify(attachmentRepository, never()).save(any());
    }
}
