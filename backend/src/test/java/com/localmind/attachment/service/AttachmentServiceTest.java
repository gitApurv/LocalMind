package com.localmind.attachment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.attachment.storage.FileStorageService;
import com.localmind.common.exception.BadRequestException;
import com.localmind.conversation.service.OwnershipGuard;
import com.localmind.support.TestProperties;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AttachmentServiceTest {

    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final DocumentEtlService documentEtlService = mock(DocumentEtlService.class);
    private final AttachmentService attachmentService = new AttachmentService(attachmentRepository, fileStorageService,
            documentEtlService, mock(OwnershipGuard.class), TestProperties.fileStorage("unused"));

    @Test
    @DisplayName("deleteConversationAttachments() should remove rows, stored files and indexed chunks")
    void deletesFilesAndVectors() {
        Attachment first = Attachment.builder().id("a1").conversationId("c1").userId("u1").fileName("one.pdf").build();
        Attachment second = Attachment.builder().id("a2").conversationId("c1").userId("u1").fileName("two.md").build();
        when(attachmentRepository.findByConversationIdOrderByCreatedAtDesc("c1")).thenReturn(List.of(first, second));

        attachmentService.deleteConversationAttachments("c1");

        verify(attachmentRepository).deleteByConversationId("c1");
        verify(fileStorageService).delete("u1", "a1", "one.pdf");
        verify(fileStorageService).delete("u1", "a2", "two.md");
        verify(documentEtlService).deleteIndexedChunks("u1", "c1");
    }

    @Test
    @DisplayName("deleteConversationAttachments() should do nothing for a conversation without attachments")
    void nothingToDelete() {
        when(attachmentRepository.findByConversationIdOrderByCreatedAtDesc("c1")).thenReturn(List.of());

        attachmentService.deleteConversationAttachments("c1");

        verify(attachmentRepository, never()).deleteByConversationId("c1");
    }

    @Test
    @DisplayName("uploadAttachment() should reject a file name longer than the column with 400")
    void rejectsOverlongFileName() {
        MockMultipartFile file = new MockMultipartFile("file", "x".repeat(252) + ".txt", "text/plain",
                "content".getBytes());

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> attachmentService.uploadAttachment("c1", "u1", file));

        assertEquals("File name must not exceed 255 characters", e.getMessage());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("uploadAttachment() should store a content type derived from the extension, not the client's")
    void derivesContentTypeFromExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "Notes.PDF", "text/html; " + "x".repeat(200),
                "%PDF".getBytes());

        attachmentService.uploadAttachment("c1", "u1", file);

        ArgumentCaptor<Attachment> saved = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(saved.capture());
        assertEquals("application/pdf", saved.getValue().getFileType());
    }

    @Test
    @DisplayName("uploadAttachment() should delete a partly stored file when storing fails and the upload rolls back")
    void removesPartialFileOnRollback() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "content".getBytes());
        doThrow(new IllegalStateException("disk full")).when(fileStorageService).store(any(InputStream.class),
                eq("u1"), anyString(), eq("notes.txt"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(IllegalStateException.class, () -> attachmentService.uploadAttachment("c1", "u1", file));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(fileStorageService).delete(eq("u1"), anyString(), eq("notes.txt"));
        verify(documentEtlService, never()).processAttachment(anyString());
    }
}
