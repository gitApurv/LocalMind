package com.localmind.conversation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ConversationRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit tests for {@link OwnershipGuard}: owner validation, 403 denials for other users, missing conversations and null
 * ids, owner-scoped loading and the locking lookup.
 */
class OwnershipGuardTest {

    private final ConversationRepository repository = mock(ConversationRepository.class);
    private final OwnershipGuard guard = new OwnershipGuard(repository);
    private final Conversation conversation = Conversation.builder().id("c1").userId("u1").title("Chat").build();

    @Test
    @DisplayName("validateConversationOwnership() passes for the owner")
    void validatesOwner() {
        when(repository.existsByIdAndUserId("c1", "u1")).thenReturn(true);

        assertDoesNotThrow(() -> guard.validateConversationOwnership("c1", "u1"));
    }

    @Test
    @DisplayName("validateConversationOwnership() denies another user, a missing conversation and null ids")
    void validateDeniesNonOwners() {
        when(repository.existsByIdAndUserId("c1", "intruder")).thenReturn(false);

        assertDenied(() -> guard.validateConversationOwnership("c1", "intruder"));
        assertDenied(() -> guard.validateConversationOwnership(null, "u1"));
        assertDenied(() -> guard.validateConversationOwnership("c1", null));
    }

    @Test
    @DisplayName("requireOwnedConversation() returns the owner's conversation from one owner-scoped query")
    void requireReturnsOwnedConversation() {
        when(repository.findByIdAndUserId("c1", "u1")).thenReturn(Optional.of(conversation));

        assertSame(conversation, guard.requireOwnedConversation("c1", "u1"));
        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("requireOwnedConversation() denies another user's or a missing conversation with 403")
    void requireDeniesNonOwners() {
        when(repository.findByIdAndUserId("c1", "intruder")).thenReturn(Optional.empty());

        assertDenied(() -> guard.requireOwnedConversation("c1", "intruder"));
        assertDenied(() -> guard.requireOwnedConversation(null, "u1"));
        verify(repository, never()).findByIdAndUserId(null, "u1");
    }

    @Test
    @DisplayName("lockOwnedConversation() uses the locking lookup and denies a conversation that no longer exists")
    void lockUsesLockingLookup() {
        when(repository.findLockedByIdAndUserId("c1", "u1")).thenReturn(Optional.of(conversation));
        when(repository.findLockedByIdAndUserId("deleted", "u1")).thenReturn(Optional.empty());

        assertSame(conversation, guard.lockOwnedConversation("c1", "u1"));
        assertDenied(() -> guard.lockOwnedConversation("deleted", "u1"));
        verify(repository, never()).findByIdAndUserId(any(), any());
    }

    private static void assertDenied(Runnable call) {
        AccessDeniedException e = assertThrows(AccessDeniedException.class, call::run);
        assertEquals(OwnershipGuard.ACCESS_DENIED_MESSAGE, e.getMessage());
    }
}
