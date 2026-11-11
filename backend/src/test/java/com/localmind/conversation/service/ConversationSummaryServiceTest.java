package com.localmind.conversation.service;

import static org.mockito.Mockito.*;

import com.localmind.conversation.entity.ChatMessage;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.llm.config.LlmProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class ConversationSummaryServiceTest {
    private final ChatModel chatModel = mock(ChatModel.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ConversationSummaryService service = new ConversationSummaryService(chatModel, conversationRepository,
            LlmProperties.defaults());

    @Test
    void updatesConversationWithGeneratedSummary() {
        Conversation conv = Conversation.builder().id("c1").userId("u1").build();
        when(conversationRepository.findById("c1")).thenReturn(Optional.of(conv));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new org.springframework.ai.chat.messages.AssistantMessage("User is planning a vacation to Paris.")))));

        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        ChatMessage u = ChatMessage.builder().role(ChatMessage.Role.USER).content("I want to visit Paris")
                .createdAt(createdAt).build();
        service.summarizeDroppedTurnsAsync("c1", List.of(u), "gemma3:1b");

        verify(conversationRepository).updateSummary("c1", "User is planning a vacation to Paris.", createdAt);
    }

    @Test
    void skipsTurnsAlreadyFoldedIntoTheSummary() {
        Instant watermark = Instant.parse("2026-01-01T10:00:00Z");
        Conversation conv = Conversation.builder().id("c1").userId("u1").summary("Old summary")
                .summarizedThrough(watermark).build();
        when(conversationRepository.findById("c1")).thenReturn(Optional.of(conv));

        ChatMessage alreadySummarized = ChatMessage.builder().role(ChatMessage.Role.USER).content("Old turn")
                .createdAt(watermark).build();
        service.summarizeDroppedTurnsAsync("c1", List.of(alreadySummarized), "gemma3:1b");

        verifyNoInteractions(chatModel);
        verify(conversationRepository, never()).updateSummary(any(), any(), any());
    }
}
