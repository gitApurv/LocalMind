package com.localmind.attachment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.attachment.storage.FileStorageService;
import com.localmind.common.ai.VectorFilters;
import com.localmind.support.RecordingVectorStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * Unit tests for {@link DocumentEtlService}: chunking and indexing with metadata, FAILED status on unreadable files,
 * and chunk cleanup when indexing, recording READY or the attachment itself goes away. Uses an in-memory vector store
 * instead of Qdrant.
 */
@ExtendWith(MockitoExtension.class)
class DocumentEtlServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private FileStorageService fileStorageService;

    private RecordingVectorStore vectorStore;
    private DocumentEtlService documentEtlService;

    @BeforeEach
    void setUp() {
        vectorStore = new RecordingVectorStore();
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
        when(attachmentRepository.updateOutcome(eq(attachmentId), eq(Attachment.Status.READY), anyInt(), isNull()))
                .thenReturn(1);
        String sampleText = "LocalMind is an open-source privacy-first conversational AI platform. "
                + "It uses Ollama for local model execution and Qdrant for RAG vector retrieval.";
        Resource mockResource = new ByteArrayResource(sampleText.getBytes(StandardCharsets.UTF_8));
        when(fileStorageService.loadAsResource("user-1", attachmentId, "test.txt")).thenReturn(mockResource);

        documentEtlService.processAttachment(attachmentId);

        List<Document> savedDocs = vectorStore.added();
        assertFalse(savedDocs.isEmpty());
        for (Document doc : savedDocs) {
            assertEquals("user-1", doc.getMetadata().get("userId"));
            assertEquals("conv-1", doc.getMetadata().get("conversationId"));
            assertEquals(attachmentId, doc.getMetadata().get("attachmentId"));
            assertEquals("test.txt", doc.getMetadata().get("fileName"));
            assertEquals("DOC_CHUNK", doc.getMetadata().get("type"));
            assertNotNull(doc.getMetadata().get("chunkIndex"));
        }

        verify(attachmentRepository).updateOutcome(attachmentId, Attachment.Status.READY, savedDocs.size(), null);
        verify(attachmentRepository, never()).save(any());
        assertEquals(0, vectorStore.deleteFilters().size());
    }

    @Test
    @DisplayName("processAttachment() with corrupted or failing file should mark status FAILED")
    void processAttachmentCorruptedFile() throws Exception {
        String attachmentId = "att-fail";
        Attachment attachment = Attachment.builder().id(attachmentId).conversationId("conv-1").userId("user-1")
                .fileName("corrupted.bin").fileType("application/octet-stream").status(Attachment.Status.PROCESSING)
                .build();

        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(attachmentRepository.updateOutcome(eq(attachmentId), eq(Attachment.Status.FAILED), eq(0), anyString()))
                .thenReturn(1);

        Resource mockBrokenResource = mock(Resource.class);
        when(mockBrokenResource.getInputStream()).thenThrow(new IOException("Disk I/O failure or corrupt payload"));
        when(fileStorageService.loadAsResource("user-1", attachmentId, "corrupted.bin")).thenReturn(mockBrokenResource);

        assertDoesNotThrow(() -> documentEtlService.processAttachment(attachmentId));

        verify(attachmentRepository).updateOutcome(eq(attachmentId), eq(Attachment.Status.FAILED), eq(0),
                argThat(message -> message.startsWith("The document could not be processed")));
        verify(attachmentRepository, never()).save(any());
        assertTrue(vectorStore.added().isEmpty());
    }

    @Test
    @DisplayName("processAttachment() should not resurrect an attachment deleted while it was processed")
    void processAttachmentDeletedDuringProcessing() {
        String attachmentId = "att-deleted";
        Attachment attachment = Attachment.builder().id(attachmentId).conversationId("conv-1").userId("user-1")
                .fileName("gone.txt").status(Attachment.Status.PROCESSING).build();
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(attachmentRepository.updateOutcome(any(), any(), anyInt(), any())).thenReturn(0);
        when(fileStorageService.loadAsResource(any(), any(), any()))
                .thenReturn(new ByteArrayResource("Some text content".getBytes(StandardCharsets.UTF_8)));

        documentEtlService.processAttachment(attachmentId);

        verify(attachmentRepository, never()).save(any());
        assertFalse(vectorStore.added().isEmpty());
        assertEquals(1, vectorStore.deleteFilters().size());
    }

    @Test
    @DisplayName("processAttachment() should delete the chunks of the attachment when indexing fails part-way")
    void partialIndexingFailureDeletesWrittenChunks() {
        Attachment attachment = readableAttachment("att-partial");
        vectorStore.failOnAddAfterFirstDocument(new IllegalStateException("Qdrant unavailable"));
        when(attachmentRepository.updateOutcome(eq("att-partial"), eq(Attachment.Status.FAILED), eq(0), anyString()))
                .thenReturn(1);

        documentEtlService.processAttachment(attachment.getId());

        assertFalse(vectorStore.added().isEmpty());
        assertEquals(List.of(VectorFilters.attachmentChunks(attachment.getUserId(), attachment.getId()).toString()),
                vectorStore.deleteFilters().stream().map(Filter.Expression::toString).toList());
        verify(attachmentRepository, never()).updateOutcome(any(), eq(Attachment.Status.READY), anyInt(), any());
    }

    @Test
    @DisplayName("processAttachment() should delete the written chunks and record FAILED when recording READY fails")
    void readyUpdateFailureDeletesChunksAndRecordsFailed() {
        Attachment attachment = readableAttachment("att-ready-fails");
        when(attachmentRepository.updateOutcome(eq("att-ready-fails"), eq(Attachment.Status.READY), anyInt(), isNull()))
                .thenThrow(new IllegalStateException("deadlock"));
        when(attachmentRepository.updateOutcome(eq("att-ready-fails"), eq(Attachment.Status.FAILED), eq(0),
                anyString())).thenReturn(1);

        documentEtlService.processAttachment(attachment.getId());

        assertFalse(vectorStore.added().isEmpty());
        assertEquals(List.of(VectorFilters.attachmentChunks(attachment.getUserId(), attachment.getId()).toString()),
                vectorStore.deleteFilters().stream().map(Filter.Expression::toString).toList());
        verify(attachmentRepository).updateOutcome(eq("att-ready-fails"), eq(Attachment.Status.FAILED), eq(0),
                anyString());
    }

    @Test
    @DisplayName("processAttachment() should never throw when the FAILED status cannot be recorded either")
    void failedUpdateFailureIsSwallowed() {
        Attachment attachment = readableAttachment("att-db-down");
        when(attachmentRepository.updateOutcome(eq("att-db-down"), any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> documentEtlService.processAttachment(attachment.getId()));

        verify(attachmentRepository).updateOutcome(eq("att-db-down"), eq(Attachment.Status.FAILED), eq(0),
                anyString());
        assertEquals(1, vectorStore.deleteFilters().size());
    }

    @Test
    @DisplayName("The chunk delete filter is scoped to the attachment's owner, id and document type")
    void attachmentChunksFilterScopesToOneAttachment() {
        Attachment attachment = Attachment.builder().id("att-1").conversationId("conv-1").userId("user-1")
                .fileName("a.txt").build();

        String filter = VectorFilters.attachmentChunks(attachment.getUserId(), attachment.getId()).toString();

        assertTrue(filter.contains("userId") && filter.contains("user-1"));
        assertTrue(filter.contains("attachmentId") && filter.contains("att-1"));
        assertTrue(filter.contains("DOC_CHUNK"));
        assertFalse(filter.contains("conversationId"));
    }

    private Attachment readableAttachment(String attachmentId) {
        Attachment attachment = Attachment.builder().id(attachmentId).conversationId("conv-1").userId("user-1")
                .fileName("doc.txt").status(Attachment.Status.PROCESSING).build();
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        String text = "LocalMind keeps documents local. It indexes them in Qdrant for retrieval augmented generation.";
        when(fileStorageService.loadAsResource("user-1", attachmentId, "doc.txt"))
                .thenReturn(new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)));
        return attachment;
    }
}
