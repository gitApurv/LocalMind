package com.localmind.conversation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.support.StubChatModel;
import com.localmind.support.TestProperties;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AutoTitleService}: title generation with quote, prefix and reasoning-block cleanup, skipping
 * customised titles, and apostrophe-safe title cleaning.
 */
@ExtendWith(MockitoExtension.class)
class AutoTitleServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    private StubChatModel chatModel;
    private AutoTitleService autoTitleService;

    @BeforeEach
    void setUp() {
        chatModel = new StubChatModel();
        autoTitleService = new AutoTitleService(chatModel, conversationRepository, TestProperties.llmDefaults());
    }

    @Test
    @DisplayName("generateTitleAsync() should update conversation title and strip quotes and prefixes")
    void generateTitleSuccess() {
        String convId = "conv-1";
        Conversation conv = Conversation.builder().id(convId).userId("u-1").title("New Chat").createdAt(Instant.now())
                .build();
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));

        chatModel.reply("\"Title: Understanding Java 21\"");

        autoTitleService.generateTitleAsync(convId, "Explain Java 21 virtual threads", "gemma3:1b");

        verify(conversationRepository).replacePlaceholderTitle(eq(conv.getId()), eq("Understanding Java 21"),
                eq(Conversation.DEFAULT_TITLE), any(Instant.class));
    }

    @Test
    @DisplayName("generateTitleAsync() should strip <think>...</think> reasoning blocks from DeepSeek-R1 responses")
    void stripReasoningBlocksFromTitle() {
        String convId = "conv-2";
        Conversation conv = Conversation.builder().id(convId).userId("u-1").title("New Chat").createdAt(Instant.now())
                .build();
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));

        chatModel.reply("<think>The user wants to know about Spring Boot setup.</think>Spring Boot Setup");

        autoTitleService.generateTitleAsync(convId, "How do I configure Spring Boot?", "deepseek-r1");

        verify(conversationRepository).replacePlaceholderTitle(eq(conv.getId()), eq("Spring Boot Setup"),
                eq(Conversation.DEFAULT_TITLE), any(Instant.class));
    }

    @Test
    @DisplayName("generateTitleAsync() should do nothing if title is already customized")
    void skipIfTitleAlreadyCustomized() {
        String convId = "conv-3";
        Conversation conv = Conversation.builder().id(convId).userId("u-1").title("Existing Thread Name")
                .createdAt(Instant.now()).build();
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));

        autoTitleService.generateTitleAsync(convId, "Some message", "gemma3:1b");

        assertEquals("Existing Thread Name", conv.getTitle());
        verify(conversationRepository, never()).replacePlaceholderTitle(any(), any(), any(), any());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {"Don't Panic Guide | Don't Panic Guide",
            "'Quoted Title' | Quoted Title", "\"Double Quoted\" | Double Quoted", "**Title:** Bold Title | Bold Title",
            "\u2018Curly Quoted\u2019 | Curly Quoted"})
    void cleanTitleKeepsApostrophesButDropsWrappingQuotes(String raw, String expected) {
        assertEquals(expected, AutoTitleService.cleanTitle(raw));
    }
}
