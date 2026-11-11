package com.localmind.conversation.service;

import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.llm.config.LlmProperties;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Replaces the placeholder title of a new conversation with a short title generated from the first prompt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoTitleService {

    private static final int MAX_PROMPT_SAMPLE_CHARS = 500;
    private static final int MAX_TITLE_CHARS = 50;
    private static final String TITLE_PREFIX = "title:";
    private static final String INSTRUCTION = """
            Summarize the following user prompt into a concise 3 to 5 word title. \
            Do not include quotes, markdown, prefixes or punctuation. Reply with ONLY the title:
            """;

    private final ChatModel chatModel;
    private final ConversationRepository conversationRepository;
    private final LlmProperties llmProperties;

    @Async
    public void generateTitleAsync(String conversationId, String firstPrompt, String modelName) {
        if (conversationId == null || firstPrompt == null || firstPrompt.isBlank()) {
            return;
        }
        conversationRepository.findById(conversationId).filter(AutoTitleService::hasPlaceholderTitle)
                .ifPresent(conversation -> generateTitle(conversation, firstPrompt, modelName));
    }

    private void generateTitle(Conversation conversation, String firstPrompt, String modelName) {
        try {
            String rawTitle = ChatClient.create(chatModel).prompt()
                    .options(OllamaChatOptions.builder().model(llmProperties.resolveModel(modelName)))
                    .user(INSTRUCTION + truncate(firstPrompt.trim(), MAX_PROMPT_SAMPLE_CHARS)).call().content();
            String title = cleanTitle(rawTitle);
            if (title.isBlank()) {
                return;
            }
            // Conditional update: a title the user set while the model was thinking is never overwritten.
            if (conversationRepository.replacePlaceholderTitle(conversation.getId(), title, Conversation.DEFAULT_TITLE,
                    Instant.now()) > 0) {
                log.info("Auto-titled conversation '{}' to: '{}'", conversation.getId(), title);
            }
        } catch (Exception e) {
            log.warn("Failed to auto-title conversation '{}': {}", conversation.getId(), e.getMessage());
        }
    }

    static String cleanTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return "";
        }
        String title = ThinkTags.strip(rawTitle).replaceAll("[\"'\n\r#*`_]", "").trim();
        if (title.toLowerCase(Locale.ROOT).startsWith(TITLE_PREFIX)) {
            title = title.substring(TITLE_PREFIX.length()).trim();
        }
        return truncate(title, MAX_TITLE_CHARS).trim();
    }

    private static boolean hasPlaceholderTitle(Conversation conversation) {
        String title = conversation.getTitle();
        return title == null || title.isBlank() || title.equalsIgnoreCase(Conversation.DEFAULT_TITLE);
    }

    private static String truncate(String text, int maxChars) {
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }
}
