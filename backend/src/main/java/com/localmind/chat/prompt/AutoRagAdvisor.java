package com.localmind.chat.prompt;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.chat.config.PromptProperties;
import com.localmind.common.ai.ChatContextKeys;
import com.localmind.common.ai.VectorFilters;
import com.localmind.common.ai.VectorMetadata;
import com.localmind.llm.model.ModelProfile;
import java.util.List;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * Step 3: prepends the most relevant document excerpts of the conversation's attachments to the user message, within
 * the model's RAG token budget.
 */
@Component
public class AutoRagAdvisor implements PromptAdvisor {

    private static final int ORDER = 300;
    private static final int TOP_K = 4;
    private static final double SIMILARITY_THRESHOLD = 0.4;
    private static final int DEFAULT_RAG_BUDGET = 2000;

    private final VectorStore vectorStore;
    private final AttachmentRepository attachmentRepository;
    private final TokenCounter tokenCounter;
    private final boolean enabled;

    public AutoRagAdvisor(VectorStore vectorStore, AttachmentRepository attachmentRepository, TokenCounter tokenCounter,
            PromptProperties properties) {
        this.vectorStore = vectorStore;
        this.attachmentRepository = attachmentRepository;
        this.tokenCounter = tokenCounter;
        this.enabled = properties.rag().auto();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        if (!enabled || request == null || request.context() == null) {
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

        try {
            String references = renderReferences(search(userId, conversationId, userText), ragBudget(request));
            if (references.isEmpty()) {
                return request;
            }
            String augmented = references + userText;
            return request.mutate().prompt(request.prompt().augmentUserMessage(message -> new UserMessage(augmented)))
                    .build();
        } catch (Exception e) {
            return request;
        }
    }

    private List<Document> search(String userId, String conversationId, String query) {
        return vectorStore.similaritySearch(
                SearchRequest.builder().query(query.trim()).topK(TOP_K).similarityThreshold(SIMILARITY_THRESHOLD)
                        .filterExpression(VectorFilters.documentChunks(userId, conversationId)).build());
    }

    private static int ragBudget(ChatClientRequest request) {
        ModelProfile profile = PromptAdvisor.modelProfile(request);
        return profile != null ? profile.ragBudget() : DEFAULT_RAG_BUDGET;
    }

    /** Always keeps the best excerpt, then adds more while they fit the budget. */
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
        String fileName = document.getMetadata() != null
                ? String.valueOf(document.getMetadata().getOrDefault(VectorMetadata.FILE_NAME, "doc"))
                : "doc";
        String snippet = document.getText() != null ? document.getText().trim() : "";
        return "<reference source=\"" + fileName + "\">\n" + snippet + "\n</reference>\n\n";
    }
}
