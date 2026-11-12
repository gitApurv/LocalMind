package com.localmind.chat.prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localmind.chat.config.PromptProperties;
import com.localmind.conversation.entity.ChatMessage;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ChatMessageRepository;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.conversation.service.ConversationSummaryService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.ObjectMapper;

class HistoryAdvisorTest {
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final TokenCounter tokenCounter = new CharacterTokenCounter();
    private final ToolNoteRenderer toolNoteRenderer = new ToolNoteRenderer(new ObjectMapper());
    private final HistoryTrimmer historyTrimmer = new HistoryTrimmer(tokenCounter, toolNoteRenderer);
    private final ConversationSummaryService summaryService = mock(ConversationSummaryService.class);
    private final HistoryAdvisor advisor = new HistoryAdvisor(chatMessageRepository, conversationRepository,
            historyTrimmer, summaryService, PromptProperties.defaults());

    @Test
    void prependsSummaryWhenConversationHasSummary() {
        Conversation conv = Conversation.builder().id("c1").userId("u1").summary("User loves TypeScript").build();
        when(conversationRepository.findById("c1")).thenReturn(Optional.of(conv));

        ChatMessage m1 = ChatMessage.builder().role(ChatMessage.Role.USER).content("Hello").build();
        ChatMessage m2 = ChatMessage.builder().role(ChatMessage.Role.ASSISTANT).content("Hi").build();
        when(chatMessageRepository.findByConversationIdAndIdNotOrderByCreatedAtAsc("c1", "current"))
                .thenReturn(List.of(m1, m2));

        ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("What do I like?")))
                .context(Map.of("conversationId", "c1", "currentMessageId", "current")).build();

        AdvisorChain chain = mock(AdvisorChain.class);
        ChatClientRequest result = advisor.before(request, chain);

        assertNotNull(result);
        List<org.springframework.ai.chat.messages.Message> instructions = result.prompt().getInstructions();
        assertTrue(instructions.size() >= 3);
        assertEquals("<summary>\nUser loves TypeScript\n</summary>", instructions.get(0).getText());
    }

    @Test
    void keepsSystemMessageFirstAndHistoryBeforeCurrentTurn() {
        when(conversationRepository.findById("c1")).thenReturn(Optional.empty());
        ChatMessage m1 = ChatMessage.builder().role(ChatMessage.Role.USER).content("Earlier question").build();
        ChatMessage m2 = ChatMessage.builder().role(ChatMessage.Role.ASSISTANT).content("Earlier answer").build();
        when(chatMessageRepository.findByConversationIdAndIdNotOrderByCreatedAtAsc("c1", "current"))
                .thenReturn(List.of(m1, m2));

        Prompt prompt = new Prompt(List.of(new SystemMessage("Rules"), new UserMessage("Current question")));
        ChatClientRequest request = ChatClientRequest.builder().prompt(prompt)
                .context(Map.of("conversationId", "c1", "currentMessageId", "current")).build();

        List<String> texts = advisor.before(request, mock(AdvisorChain.class)).prompt().getInstructions().stream()
                .map(org.springframework.ai.chat.messages.Message::getText).toList();

        assertEquals(List.of("Rules", "Earlier question", "Earlier answer", "Current question"), texts);
    }

    @Test
    void sendsTurnsThatDoNotFitToTheSummaryService() {
        when(conversationRepository.findById("c1")).thenReturn(Optional.empty());
        ChatMessage old = ChatMessage.builder().role(ChatMessage.Role.USER).content("x".repeat(40_000)).build();
        ChatMessage recent = ChatMessage.builder().role(ChatMessage.Role.USER).content("Recent").build();
        when(chatMessageRepository.findByConversationIdAndIdNotOrderByCreatedAtAsc("c1", "current"))
                .thenReturn(List.of(old, recent));

        ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(new UserMessage("Now")))
                .context(Map.of("conversationId", "c1", "currentMessageId", "current")).build();
        advisor.before(request, mock(AdvisorChain.class));

        verify(summaryService).summarizeDroppedTurnsAsync(eq("c1"), eq(List.of(old)), any());
    }
}
