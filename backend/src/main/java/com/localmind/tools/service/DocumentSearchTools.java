package com.localmind.tools.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.localmind.common.ai.ChatContextKeys;
import com.localmind.common.ai.VectorFilters;
import com.localmind.common.ai.VectorMetadata;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentSearchTools {

    private static final String NAME = "searchUploadedDocs";
    private static final String DESCRIPTION = "Search uploaded documents and file attachments in the current conversation "
            + "for relevant information, excerpts, and answers.";
    private static final int TOP_K = 4;
    private static final double SIMILARITY_THRESHOLD = 0.4;

    private final VectorStore vectorStore;

    public record DocumentSearchRequest(@JsonProperty(value = "query", required = true) String query) {
    }

    public String searchUploadedDocs(String query, ToolContext toolContext) {
        if (query == null || query.isBlank()) {
            return "Error: Document search query must not be null or blank.";
        }
        String userId = ToolContexts.get(toolContext, ChatContextKeys.USER_ID);
        String conversationId = ToolContexts.get(toolContext, ChatContextKeys.CONVERSATION_ID);
        if (userId == null || conversationId == null) {
            log.warn("Cannot search documents: missing userId ({}) or conversationId ({}) in ToolContext", userId,
                    conversationId);
            return "Error: Security context missing authenticated user ID or conversation ID.";
        }

        String trimmedQuery = query.trim();
        try {
            List<Document> matches = vectorStore.similaritySearch(
                    SearchRequest.builder().query(trimmedQuery).topK(TOP_K).similarityThreshold(SIMILARITY_THRESHOLD)
                            .filterExpression(VectorFilters.documentChunks(userId, conversationId)).build());
            if (matches == null || matches.isEmpty()) {
                return "No relevant information found in uploaded documents for query: '" + trimmedQuery + "'.";
            }
            return formatMatches(trimmedQuery, matches);
        } catch (Exception e) {
            log.error("Error searching documents for query '{}': {}", trimmedQuery, e.getMessage(), e);
            return "Error: uploaded documents could not be searched right now.";
        }
    }

    private static String formatMatches(String query, List<Document> matches) {
        StringBuilder text = new StringBuilder().append("Found ").append(matches.size())
                .append(" relevant excerpt(s) in uploaded documents for '").append(query).append("':\n\n");
        for (int i = 0; i < matches.size(); i++) {
            Document document = matches.get(i);
            Object fileName = document.getMetadata() != null
                    ? document.getMetadata().get(VectorMetadata.FILE_NAME)
                    : null;
            text.append("--- Excerpt ").append(i + 1).append(" (Source: ")
                    .append(fileName != null ? fileName : "Unknown Document").append(") ---\n")
                    .append(document.getText() != null ? document.getText().trim() : "").append("\n\n");
        }
        return text.toString().trim();
    }

    public ToolCallback toolCallback() {
        return FunctionToolCallback
                .builder(NAME,
                        (DocumentSearchRequest request, ToolContext toolContext) -> searchUploadedDocs(
                                request != null ? request.query() : null, toolContext))
                .description(DESCRIPTION).inputType(DocumentSearchRequest.class).build();
    }
}
