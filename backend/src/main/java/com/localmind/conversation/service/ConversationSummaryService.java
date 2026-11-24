package com.localmind.conversation.service;

import com.localmind.conversation.entity.ChatMessage;
import com.localmind.conversation.entity.Conversation;
import com.localmind.conversation.repository.ConversationRepository;
import com.localmind.llm.config.LlmProperties;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Folds history turns that no longer fit the context window into a rolling conversation summary. Turns at or before
 * {@link Conversation#getSummarizedThrough()} are already part of the summary and are skipped. Runs asynchronously and
 * stores the result through a conditional update, so a slower, older job never overwrites a newer summary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private static final String INSTRUCTION = "Progressively summarize the conversation. Include key facts and intent "
            + "concisely (2-3 sentences max). Reply ONLY with the updated summary:\n";

    private final ChatModel chatModel;
    private final ConversationRepository conversationRepository;
    private final LlmProperties llmProperties;

    @Async
    public void summarizeDroppedTurnsAsync(String conversationId, List<ChatMessage> droppedTurns, String modelName) {
        if (conversationId == null || droppedTurns == null || droppedTurns.isEmpty()) {
            return;
        }
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            List<ChatMessage> newTurns = unsummarized(conversation, droppedTurns);
            if (!newTurns.isEmpty()) {
                summarize(conversation, newTurns, modelName);
            }
        });
    }

    private static List<ChatMessage> unsummarized(Conversation conversation, List<ChatMessage> droppedTurns) {
        Instant through = conversation.getSummarizedThrough();
        return droppedTurns.stream()
                .filter(turn -> through == null || turn.getCreatedAt() == null || turn.getCreatedAt().isAfter(through))
                .toList();
    }

    private void summarize(Conversation conversation, List<ChatMessage> newTurns, String modelName) {
        try {
            String transcript = buildTranscript(conversation, newTurns);
            String resolvedModel = llmProperties.resolveModel(modelName);
            OllamaChatOptions options = OllamaChatOptions.builder().model(resolvedModel).build();
            Prompt prompt = new Prompt(INSTRUCTION + transcript, options);
            ChatResponse response = chatModel.call(prompt);
            String summary = extractText(response);
            if (summary == null || summary.isBlank()) {
                return;
            }
            String cleanSummary = ThinkTags.strip(summary);
            Instant summarizedThrough = newTurns.getLast().getCreatedAt();
            if (cleanSummary.isBlank() || summarizedThrough == null) {
                return;
            }
            int updated = conversationRepository.updateSummary(conversation.getId(), cleanSummary,
                    summarizedThrough);
            if (updated > 0) {
                log.info("Updated rolling summary for conversation '{}'", conversation.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to summarize dropped turns for conversation '{}': {}", conversation.getId(),
                    e.getMessage());
        }
    }

    private static String buildTranscript(Conversation conversation, List<ChatMessage> turns) {
        StringBuilder transcript = new StringBuilder();
        if (conversation.hasSummary()) {
            transcript.append("Existing summary: ").append(conversation.getSummary()).append("\n\n");
        }
        transcript.append("New conversation turns to incorporate:\n");
        turns.forEach(turn -> transcript.append(turn.getRole()).append(": ").append(turn.getContent()).append("\n"));
        return transcript.toString();
    }

    private static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }
}
