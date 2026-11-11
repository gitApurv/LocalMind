package com.localmind.conversation.service;

import com.localmind.conversation.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * IDOR guardrail: every conversation-scoped operation must call this before touching data.
 */
@Component
@RequiredArgsConstructor
public class OwnershipGuard {

    private final ConversationRepository conversationRepository;

    public void validateConversationOwnership(String conversationId, String userId) {
        if (conversationId == null || userId == null
                || !conversationRepository.existsByIdAndUserId(conversationId, userId)) {
            throw new AccessDeniedException("Access denied: Conversation does not belong to user");
        }
    }
}
