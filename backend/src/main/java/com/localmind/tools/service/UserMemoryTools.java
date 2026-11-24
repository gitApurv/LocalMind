package com.localmind.tools.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.localmind.common.ai.ChatContextKeys;
import com.localmind.common.ai.VectorFilters;
import com.localmind.common.ai.VectorSearchDefaults;
import com.localmind.memory.entity.UserMemory;
import com.localmind.memory.repository.UserMemoryRepository;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

/**
 * LLM tools {@code saveUserMemory}, {@code recallUserMemories}: let the model save and recall long-term facts about the
 * user. Facts are stored relationally and embedded for semantic recall.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserMemoryTools {

    private static final String SAVE_NAME = "saveUserMemory";
    private static final String RECALL_NAME = "recallUserMemories";
    private static final String SAVE_DESCRIPTION = "Save a factual memory or personal preference about the user for future "
            + "recall across conversations.";
    private static final String RECALL_DESCRIPTION = "Recall or retrieve previously saved user facts, personal preferences, "
            + "and notes relevant to a topic or query.";
    private static final String MISSING_USER = "Error: Security context missing authenticated user ID.";
    static final int MAX_FACT_LENGTH = 1000;
    static final int MAX_LISTED_MEMORIES = 20;

    private final UserMemoryRepository userMemoryRepository;
    private final VectorStore vectorStore;
    private final UserMemoryWriteService userMemoryWriteService;

    public record SaveMemoryRequest(@JsonProperty(value = "fact", required = true) String fact) {
    }

    public record RecallMemoryRequest(@JsonProperty(value = "query", required = false) String query) {
    }

    public String saveUserMemory(String fact, ToolContext toolContext) {
        if (fact == null || fact.isBlank()) {
            return "Error: Memory fact must not be null or blank.";
        }
        String userId = ToolContexts.get(toolContext, ChatContextKeys.USER_ID);
        if (userId == null) {
            log.warn("Cannot save user memory: missing userId in ToolContext");
            return MISSING_USER;
        }

        String trimmedFact = fact.trim();
        if (trimmedFact.length() > MAX_FACT_LENGTH) {
            return "Error: Memory fact is too long (more than " + MAX_FACT_LENGTH
                    + " characters). Save a shorter summary of the fact.";
        }
        try {
            boolean saved = userMemoryWriteService.saveIfAbsent(userId, trimmedFact);
            return saved ? "Memory saved successfully: " + trimmedFact : "Memory already saved: " + trimmedFact;
        } catch (Exception e) {
            log.error("Failed to save memory for user '{}': {}", userId, e.getMessage(), e);
            return "Error: the memory could not be saved right now.";
        }
    }

    public String recallUserMemories(String query, ToolContext toolContext) {
        String userId = ToolContexts.get(toolContext, ChatContextKeys.USER_ID);
        if (userId == null) {
            log.warn("Cannot recall user memory: missing userId in ToolContext");
            return MISSING_USER;
        }

        try {
            if (query != null && !query.isBlank()) {
                List<Document> matches = searchMemories(userId, query.trim());
                if (!matches.isEmpty()) {
                    String header = "Recalled User Memories for '" + query.trim() + "':";
                    return numberedList(header, matches, Document::getText);
                }
            }
            List<UserMemory> allMemories = userMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
            if (allMemories == null || allMemories.isEmpty()) {
                return "No saved user memories found.";
            }
            List<UserMemory> listed = allMemories.stream().limit(MAX_LISTED_MEMORIES).toList();
            String header = listed.size() < allMemories.size()
                    ? "Saved User Memories (newest " + listed.size() + " of " + allMemories.size() + "):"
                    : "Saved User Memories:";
            return numberedList(header, listed, UserMemory::getMemoryFact);
        } catch (Exception e) {
            log.error("Failed to recall memories for user '{}': {}", userId, e.getMessage(), e);
            return "Error: saved memories could not be recalled right now.";
        }
    }

    private List<Document> searchMemories(String userId, String query) {
        try {
            Filter.Expression filter = VectorFilters.userMemories(userId);
            SearchRequest searchRequest = SearchRequest.builder().query(query).topK(VectorSearchDefaults.MEMORY_TOP_K)
                    .similarityThreshold(VectorSearchDefaults.MEMORY_RECALL_SIMILARITY_THRESHOLD)
                    .filterExpression(filter).build();
            List<Document> matches = vectorStore.similaritySearch(searchRequest);
            return matches != null ? matches : List.of();
        } catch (Exception e) {
            log.debug("Semantic memory search failed, falling back to relational database: {}", e.getMessage());
            return List.of();
        }
    }

    private static <T> String numberedList(String header, List<T> items, Function<T, String> text) {
        StringBuilder list = new StringBuilder(header).append("\n");
        for (int i = 0; i < items.size(); i++) {
            list.append(i + 1).append(". ").append(text.apply(items.get(i))).append("\n");
        }
        return list.toString().trim();
    }

    public ToolCallback saveToolCallback() {
        return FunctionToolCallback
                .builder(SAVE_NAME,
                        (SaveMemoryRequest request, ToolContext toolContext) -> saveUserMemory(
                                request != null ? request.fact() : null, toolContext))
                .description(SAVE_DESCRIPTION).inputType(SaveMemoryRequest.class).build();
    }

    public ToolCallback recallToolCallback() {
        return FunctionToolCallback
                .builder(RECALL_NAME,
                        (RecallMemoryRequest request, ToolContext toolContext) -> recallUserMemories(
                                request != null ? request.query() : null, toolContext))
                .description(RECALL_DESCRIPTION).inputType(RecallMemoryRequest.class).build();
    }
}
