package com.localmind.chat.prompt;

import static org.junit.jupiter.api.Assertions.*;

import com.localmind.conversation.entity.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoryTrimmerTest {
    private final TokenCounter tokenCounter = new CharacterTokenCounter();
    private final ToolNoteRenderer toolNoteRenderer = new ToolNoteRenderer(new ObjectMapper());
    private final HistoryTrimmer trimmer = new HistoryTrimmer(tokenCounter, toolNoteRenderer);

    @Test
    void trimPreservesUserAssistantPairs() {
        ChatMessage u1 = ChatMessage.builder().role(ChatMessage.Role.USER).content("Hello 1").build();
        ChatMessage a1 = ChatMessage.builder().role(ChatMessage.Role.ASSISTANT).content("Hi 1").build();
        ChatMessage u2 = ChatMessage.builder().role(ChatMessage.Role.USER).content("Hello 2").build();
        ChatMessage a2 = ChatMessage.builder().role(ChatMessage.Role.ASSISTANT).content("Hi 2").build();

        List<ChatMessage> history = List.of(u1, a1, u2, a2);
        // Budget only allows 1 pair (2 tokens).
        int budget = tokenCounter.estimate("Hello 2") + tokenCounter.estimate("Hi 2");
        TrimmedHistory result = trimmer.trimToBudget(history, budget);
        List<org.springframework.ai.chat.messages.Message> trimmed = result.messages();

        assertEquals(2, trimmed.size());
        assertEquals(List.of(u1, a1), result.dropped());
        assertEquals("Hello 2", trimmed.get(0).getText());
        assertEquals("Hi 2", trimmed.get(1).getText());
    }

    @Test
    void skipsUserMessagesThatNeverGotAReply() {
        ChatMessage u1 = ChatMessage.builder().role(ChatMessage.Role.USER).content("Hello 1").build();
        ChatMessage a1 = ChatMessage.builder().role(ChatMessage.Role.ASSISTANT).content("Hi 1").build();
        ChatMessage failed = ChatMessage.builder().role(ChatMessage.Role.USER).content("Failed question").build();
        ChatMessage u2 = ChatMessage.builder().role(ChatMessage.Role.USER).content("Hello 2").build();
        ChatMessage a2 = ChatMessage.builder().role(ChatMessage.Role.ASSISTANT).content("Hi 2").build();
        ChatMessage lastFailed = ChatMessage.builder().role(ChatMessage.Role.USER).content("Cut off").build();

        TrimmedHistory result = trimmer.trimToBudget(List.of(u1, a1, failed, u2, a2, lastFailed), 10_000);

        assertEquals(List.of("Hello 1", "Hi 1", "Hello 2", "Hi 2"),
                result.messages().stream().map(org.springframework.ai.chat.messages.Message::getText).toList());
        assertEquals(List.of(), result.dropped());
    }

    @Test
    void unansweredPromptsAreNotSentForSummarising() {
        ChatMessage failed = ChatMessage.builder().role(ChatMessage.Role.USER).content("x".repeat(400)).build();
        ChatMessage u1 = ChatMessage.builder().role(ChatMessage.Role.USER).content("Hello").build();
        ChatMessage a1 = ChatMessage.builder().role(ChatMessage.Role.ASSISTANT).content("Hi").build();

        TrimmedHistory result = trimmer.trimToBudget(List.of(failed, u1, a1), 0);

        assertEquals(List.of(u1, a1), result.dropped());
    }
}
