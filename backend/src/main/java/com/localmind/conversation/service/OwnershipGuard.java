package com.localmind.conversation.service;

import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ConversationRepository;
import java.util.Optional;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Checks that a conversation belongs to the calling user (IDOR guard); every conversation-scoped operation must call it
 * before touching data. A missing conversation and another user's conversation are both {@code 403}, so ids of other
 * users cannot be probed.
 */
@Component
@RequiredArgsConstructor
public class OwnershipGuard {

    static final String ACCESS_DENIED_MESSAGE = "Access denied: Conversation does not belong to user";

    private final ConversationRepository conversationRepository;

    public void validateConversationOwnership(String conversationId, String userId) {
        if (conversationId == null || userId == null
                || !conversationRepository.existsByIdAndUserId(conversationId, userId)) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }
    }

    public Conversation requireOwnedConversation(String conversationId, String userId) {
        return find(conversationId, userId, conversationRepository::findByIdAndUserId);
    }

    public Conversation lockOwnedConversation(String conversationId, String userId) {
        return find(conversationId, userId, conversationRepository::findLockedByIdAndUserId);
    }

    private static Conversation find(String conversationId, String userId,
            BiFunction<String, String, Optional<Conversation>> lookup) {
        if (conversationId == null || userId == null) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }
        return lookup.apply(conversationId, userId).orElseThrow(() -> new AccessDeniedException(ACCESS_DENIED_MESSAGE));
    }
}
