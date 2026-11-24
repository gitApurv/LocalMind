package com.localmind.chat.prompt.advisor;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.chat.config.PromptProperties;
import com.localmind.chat.prompt.PromptAdvisor;
import com.localmind.chat.prompt.token.TokenCounter;
import com.localmind.common.ai.ChatContextKeys;
import com.localmind.common.ai.VectorFilters;
import com.localmind.common.ai.VectorMetadata;
import com.localmind.common.ai.VectorSearchDefaults;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

/**
 * Prompt step 3 (order 300): prepends the most relevant document excerpts of the conversation's attachments to the user
 * message, within the model's RAG token budget. Runs only when the conversation has a {@code READY} attachment;
 * switched off by {@code localmind.prompt.rag.auto}.
 */
@Component
@RequiredArgsConstructor
public class AutoRagAdvisor implements PromptAdvisor {

    private static final int ORDER = 300;

    private final VectorStore vectorStore;
    private final AttachmentRepository attachmentRepository;
    private final TokenCounter tokenCounter;
    private final PromptProperties properties;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientRequest apply(ChatClientRequest request, AdvisorChain chain) {
        if (!properties.rag().auto() || request == null || request.context() == null) {
            return request;
        }
        String conversationId = PromptAdvisor.contextString(request, ChatContextKeys.CONVERSATION_ID);
        String userId = PromptAdvisor.contextString(request, ChatContextKeys.USER_ID);
        if (conversationId == null || userId == null
                || !attachmentRepository.existsByConversationIdAndStatus(conversationId, Attachment.Status.READY)) {
            return request;
        }
        String userText = PromptAdvisor.userText(request);
        if (userText == null) {
            return request;
        }

        List<Document> documents = search(userId, conversationId, userText);
        int budget = PromptAdvisor.budgetProfile(request).ragBudget();
        String references = renderReferences(documents, budget);
        if (references.isEmpty()) {
            return request;
        }
        String augmented = references + userText;
        Prompt augmentedPrompt = request.prompt().augmentUserMessage(message -> new UserMessage(augmented));
        return request.mutate().prompt(augmentedPrompt).build();
    }

    private List<Document> search(String userId, String conversationId, String query) {
        Filter.Expression filter = VectorFilters.documentChunks(userId, conversationId);
        SearchRequest searchRequest = SearchRequest.builder().query(query.trim())
                .topK(VectorSearchDefaults.DOCUMENT_TOP_K)
                .similarityThreshold(VectorSearchDefaults.DOCUMENT_SIMILARITY_THRESHOLD).filterExpression(filter)
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }

    private String renderReferences(List<Document> documents, int budget) {
        if (documents == null) {
            return "";
        }
        StringBuilder references = new StringBuilder();
        int usedTokens = 0;
        for (Document document : documents) {
            String block = referenceBlock(document);
            int blockTokens = tokenCounter.estimate(block);
            if (usedTokens + blockTokens > budget && usedTokens > 0) {
                break;
            }
            references.append(block);
            usedTokens += blockTokens;
        }
        return references.toString();
    }

    private static String referenceBlock(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String fileName = metadata != null
                ? String.valueOf(metadata.getOrDefault(VectorMetadata.FILE_NAME, "doc"))
                : "doc";
        String snippet = document.getText() != null ? document.getText().trim() : "";
        return "<reference source=\"" + fileName + "\">\n" + snippet + "\n</reference>\n\n";
    }
}
