package com.localmind.conversation.service;

import com.localmind.attachment.service.AttachmentService;
import com.localmind.common.exception.BadRequestException;
import com.localmind.common.exception.ResourceNotFoundException;
import com.localmind.conversation.dto.ConversationResponse;
import com.localmind.conversation.dto.CreateConversationRequest;
import com.localmind.conversation.dto.MessageResponse;
import com.localmind.conversation.dto.UpdateConversationRequest;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ChatMessageRepository;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.llm.config.LlmProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AttachmentService attachmentService;
    private final OwnershipGuard ownershipGuard;
    private final LlmProperties llmProperties;

    @Transactional
    public ConversationResponse createConversation(String userId, CreateConversationRequest request) {
        String title = request != null && request.title() != null && !request.title().isBlank()
                ? request.title().trim()
                : Conversation.DEFAULT_TITLE;
        String modelName = llmProperties.resolveModel(request != null ? request.modelName() : null);

        Conversation conversation = Conversation.builder().userId(userId).title(title).modelName(modelName).build();
        return ConversationResponse.from(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(String userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(ConversationResponse::from)
                .toList();
    }

    @Transactional
    public ConversationResponse updateConversation(String conversationId, String userId,
            UpdateConversationRequest request) {
        ownershipGuard.validateConversationOwnership(conversationId, userId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BadRequestException("Title must not be blank");
            }
            conversation.setTitle(request.title().trim());
        }
        if (request.pinned() != null) {
            conversation.setPinned(request.pinned());
        }
        if (request.modelName() != null && !request.modelName().isBlank()) {
            conversation.setModelName(request.modelName().trim());
        }
        return ConversationResponse.from(conversationRepository.saveAndFlush(conversation));
    }

    @Transactional
    public void deleteConversation(String conversationId, String userId) {
        ownershipGuard.validateConversationOwnership(conversationId, userId);
        attachmentService.deleteConversationAttachments(conversationId);
        chatMessageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getConversationMessages(String conversationId, String userId) {
        ownershipGuard.validateConversationOwnership(conversationId, userId);
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(MessageResponse::from).toList();
    }
}
