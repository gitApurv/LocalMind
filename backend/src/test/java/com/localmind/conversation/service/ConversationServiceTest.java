package com.localmind.conversation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.attachment.service.AttachmentService;
import com.localmind.attachment.service.DocumentEtlService;
import com.localmind.attachment.storage.FileStorageService;
import com.localmind.common.exception.BadRequestException;
import com.localmind.conversation.dto.ConversationResponse;
import com.localmind.conversation.dto.CreateConversationRequest;
import com.localmind.conversation.dto.UpdateConversationRequest;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ChatMessageRepository;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.support.TestProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit tests for {@link ConversationService}: creation with defaults, rename, pin and partial updates, blank-title
 * rejection, cascading delete of attachments, files, chunks and messages, and ownership checks before reads and
 * deletes.
 */
class ConversationServiceTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final DocumentEtlService documentEtlService = mock(DocumentEtlService.class);
    private final OwnershipGuard ownershipGuard = mock(OwnershipGuard.class);
    private final AttachmentService attachmentService = new AttachmentService(attachmentRepository, fileStorageService,
            documentEtlService, ownershipGuard, TestProperties.fileStorage("unused"));
    private final ConversationService service = new ConversationService(conversationRepository, chatMessageRepository,
            attachmentService, ownershipGuard, TestProperties.llmDefaults());

    private final Conversation conversation = Conversation.builder().id("c1").userId("u1").title("Old title")
            .modelName("gemma3:1b").build();

    @Test
    @DisplayName("createConversation() trims the title and model and scopes the conversation to the caller")
    void createsConversation() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(call -> call.getArgument(0));

        ConversationResponse created = service.createConversation("u1",
                new CreateConversationRequest("  Trip planning  ", " qwen2.5:7b "));

        assertEquals("u1", created.userId());
        assertEquals("Trip planning", created.title());
        assertEquals("qwen2.5:7b", created.modelName());
        assertFalse(created.pinned());
    }

    @Test
    @DisplayName("createConversation() without a body uses the placeholder title and the default model")
    void createsConversationWithDefaults() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(call -> call.getArgument(0));

        ConversationResponse created = service.createConversation("u1", null);

        assertEquals(Conversation.DEFAULT_TITLE, created.title());
        assertEquals(TestProperties.llmDefaults().defaultModel(), created.modelName());
    }

    @Test
    @DisplayName("updateConversation() renames and pins the owned conversation loaded by the guard")
    void renamesAndPins() {
        when(ownershipGuard.requireOwnedConversation("c1", "u1")).thenReturn(conversation);
        when(conversationRepository.saveAndFlush(conversation)).thenReturn(conversation);

        ConversationResponse updated = service.updateConversation("c1", "u1",
                new UpdateConversationRequest("  New title ", true, null));

        assertEquals("New title", updated.title());
        assertTrue(updated.pinned());
        assertEquals("gemma3:1b", updated.modelName());
        verify(conversationRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("updateConversation() leaves fields that were not sent unchanged and ignores a blank model")
    void partialUpdate() {
        conversation.setPinned(true);
        when(ownershipGuard.requireOwnedConversation("c1", "u1")).thenReturn(conversation);
        when(conversationRepository.saveAndFlush(conversation)).thenReturn(conversation);

        ConversationResponse updated = service.updateConversation("c1", "u1",
                new UpdateConversationRequest(null, null, "  "));

        assertEquals("Old title", updated.title());
        assertTrue(updated.pinned());
        assertEquals("gemma3:1b", updated.modelName());
    }

    @Test
    @DisplayName("updateConversation() rejects a blank title with 400 and saves nothing")
    void rejectsBlankTitle() {
        when(ownershipGuard.requireOwnedConversation("c1", "u1")).thenReturn(conversation);

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.updateConversation("c1", "u1", new UpdateConversationRequest("   ", null, null)));

        assertEquals("Title must not be blank", e.getMessage());
        verify(conversationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("deleteConversation() locks the conversation, then deletes attachments, files, chunks and messages")
    void deleteCascades() {
        Attachment first = Attachment.builder().id("a1").conversationId("c1").userId("u1").fileName("one.pdf").build();
        Attachment second = Attachment.builder().id("a2").conversationId("c1").userId("u1").fileName("two.md").build();
        when(ownershipGuard.lockOwnedConversation("c1", "u1")).thenReturn(conversation);
        when(attachmentRepository.findByConversationIdOrderByCreatedAtDesc("c1")).thenReturn(List.of(first, second));

        service.deleteConversation("c1", "u1");

        InOrder order = inOrder(ownershipGuard, attachmentRepository, chatMessageRepository, conversationRepository);
        order.verify(ownershipGuard).lockOwnedConversation("c1", "u1");
        order.verify(attachmentRepository).findByConversationIdOrderByCreatedAtDesc("c1");
        order.verify(attachmentRepository).deleteByConversationId("c1");
        order.verify(chatMessageRepository).deleteByConversationId("c1");
        order.verify(conversationRepository).delete(conversation);
        verify(fileStorageService).delete("u1", "a1", "one.pdf");
        verify(fileStorageService).delete("u1", "a2", "two.md");
        verify(documentEtlService).deleteIndexedChunks("u1", "c1");
    }

    @Test
    @DisplayName("deleteConversation() of another user's or an already deleted conversation deletes nothing")
    void deleteDeniedLeavesEverything() {
        when(ownershipGuard.lockOwnedConversation("c1", "intruder")).thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class, () -> service.deleteConversation("c1", "intruder"));

        verify(attachmentRepository, never()).deleteByConversationId(anyString());
        verify(chatMessageRepository, never()).deleteByConversationId(anyString());
        verify(conversationRepository, never()).delete(any(Conversation.class));
    }

    @Test
    @DisplayName("getConversationMessages() checks ownership before reading")
    void messagesRequireOwnership() {
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc("c1")).thenReturn(List.of());

        assertTrue(service.getConversationMessages("c1", "u1").isEmpty());

        verify(ownershipGuard).validateConversationOwnership("c1", "u1");
    }
}
