package com.localmind.attachment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localmind.attachment.config.FileStorageProperties;
import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.model.AttachmentDownload;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.attachment.storage.FileStorageService;
import com.localmind.common.exception.BadRequestException;
import com.localmind.conversation.service.OwnershipGuard;
import com.localmind.support.TestProperties;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

/**
 * Unit tests for {@link AttachmentService}: conversation cleanup of rows, files and chunks, upload validation and
 * locking, partial-file removal on rollback, and download ownership checks.
 */
class AttachmentServiceTest {

    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final DocumentEtlService documentEtlService = mock(DocumentEtlService.class);
    private final OwnershipGuard ownershipGuard = mock(OwnershipGuard.class);
    private final AttachmentService attachmentService = new AttachmentService(attachmentRepository, fileStorageService,
            documentEtlService, ownershipGuard, TestProperties.fileStorage("unused"));

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

    @Test
    @DisplayName("uploadAttachment() should lock the conversation row before validating or storing anything")
    void uploadLocksConversation() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "content".getBytes());

        attachmentService.uploadAttachment("c1", "u1", file);

        verify(ownershipGuard).lockOwnedConversation("c1", "u1");
        verify(ownershipGuard, never()).validateConversationOwnership(anyString(), anyString());
    }

    @Test
    @DisplayName("uploadAttachment() into a missing (e.g. just deleted) conversation should fail before storing")
    void uploadToMissingConversationIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "content".getBytes());
        when(ownershipGuard.lockOwnedConversation("gone", "u1")).thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class, () -> attachmentService.uploadAttachment("gone", "u1", file));

        verify(fileStorageService, never()).store(any(), anyString(), anyString(), anyString());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("uploadAttachment() should reject an extension that is not allowed with 400")
    void rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "tool.exe", "application/octet-stream",
                "MZ".getBytes());

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> attachmentService.uploadAttachment("c1", "u1", file));

        assertEquals("Unsupported file format 'exe'. Allowed: [pdf, txt, docx, md]", e.getMessage());
        verify(fileStorageService, never()).store(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("uploadAttachment() should reject a file larger than max-file-size with 400")
    void rejectsOversizeFile() {
        AttachmentService smallLimit = new AttachmentService(attachmentRepository, fileStorageService,
                documentEtlService, ownershipGuard,
                new FileStorageProperties("unused", DataSize.ofMegabytes(1), null));
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain",
                new byte[(int) DataSize.ofMegabytes(1).toBytes() + 1]);

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> smallLimit.uploadAttachment("c1", "u1", file));

        assertEquals("File exceeds maximum size limit of 1MB", e.getMessage());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAttachmentDownload() should return the file, its name and a type derived from the extension")
    void downloadsOwnAttachment() {
        Attachment attachment = Attachment.builder().id("a1").conversationId("c1").userId("u1").fileName("Guide.MD")
                .fileType("text/html").build();
        Resource resource = new ByteArrayResource("# Guide".getBytes());
        when(attachmentRepository.findById("a1")).thenReturn(Optional.of(attachment));
        when(fileStorageService.loadAsResource("u1", "a1", "Guide.MD")).thenReturn(resource);

        AttachmentDownload download = attachmentService.getAttachmentDownload("c1", "a1", "u1");

        assertSame(resource, download.resource());
        assertEquals("Guide.MD", download.fileName());
        assertEquals("text/markdown", download.contentType());
        verify(ownershipGuard).validateConversationOwnership("c1", "u1");
    }

    @Test
    @DisplayName("getAttachmentDownload() should deny an unknown attachment (403)")
    void unknownAttachmentIsDenied() {
        when(attachmentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> attachmentService.getAttachmentDownload("c1", "missing", "u1"));
    }

    @Test
    @DisplayName("getAttachmentDownload() should deny the caller's attachment of another conversation (403)")
    void attachmentOfOtherConversationIsDenied() {
        when(attachmentRepository.findById("a1")).thenReturn(Optional.of(
                Attachment.builder().id("a1").conversationId("c2").userId("u1").fileName("one.pdf").build()));

        assertThrows(AccessDeniedException.class, () -> attachmentService.getAttachmentDownload("c1", "a1", "u1"));
        verify(fileStorageService, never()).loadAsResource(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("getAttachmentDownload() should reject another user's attachment with 403")
    void otherUsersAttachmentIsForbidden() {
        when(attachmentRepository.findById("a1")).thenReturn(Optional.of(
                Attachment.builder().id("a1").conversationId("c1").userId("someone-else").fileName("one.pdf").build()));

        assertThrows(AccessDeniedException.class, () -> attachmentService.getAttachmentDownload("c1", "a1", "u1"));
        verify(fileStorageService, never()).loadAsResource(anyString(), anyString(), anyString());
    }
}
