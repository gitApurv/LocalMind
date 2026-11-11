package com.localmind.conversation.controller;

import com.localmind.common.security.CurrentUserId;
import com.localmind.conversation.dto.ConversationResponse;
import com.localmind.conversation.dto.CreateConversationRequest;
import com.localmind.conversation.dto.MessageResponse;
import com.localmind.conversation.dto.UpdateConversationRequest;
import com.localmind.conversation.service.ConversationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createConversation(@CurrentUserId String userId,
            @Valid @RequestBody(required = false) CreateConversationRequest request) {
        return conversationService.createConversation(userId, request);
    }

    @GetMapping
    public List<ConversationResponse> listConversations(@CurrentUserId String userId) {
        return conversationService.listConversations(userId);
    }

    @PatchMapping("/{id}")
    public ConversationResponse updateConversation(@CurrentUserId String userId, @PathVariable("id") String id,
            @Valid @RequestBody UpdateConversationRequest request) {
        return conversationService.updateConversation(id, userId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@CurrentUserId String userId, @PathVariable("id") String id) {
        conversationService.deleteConversation(id, userId);
    }

    @GetMapping("/{id}/messages")
    public List<MessageResponse> getMessages(@CurrentUserId String userId, @PathVariable("id") String id) {
        return conversationService.getConversationMessages(id, userId);
    }
}
