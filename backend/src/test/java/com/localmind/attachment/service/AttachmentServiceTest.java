package com.localmind.attachment.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localmind.attachment.config.FileStorageProperties;
import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.attachment.storage.FileStorageService;
import com.localmind.conversation.service.OwnershipGuard;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttachmentServiceTest {

    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final DocumentEtlService documentEtlService = mock(DocumentEtlService.class);
    private final AttachmentService attachmentService = new AttachmentService(attachmentRepository, fileStorageService,
            documentEtlService, mock(OwnershipGuard.class), FileStorageProperties.withUploadDir("unused"));

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
}
