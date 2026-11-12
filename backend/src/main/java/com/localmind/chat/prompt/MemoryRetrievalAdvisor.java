package com.localmind.chat.prompt;

import com.localmind.chat.config.PromptProperties;
import com.localmind.common.ai.ChatContextKeys;
import com.localmind.common.ai.VectorFilters;
import com.localmind.memory.repository.UserMemoryRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * Step 2: appends semantically relevant (non-pinned) user memories to the system message.
 */
@Component
public class MemoryRetrievalAdvisor implements PromptAdvisor {

    private static final int ORDER = 200;
    private static final int TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.4;
    private static final int MAX_MEMORY_TOKENS = 200;

    private final VectorStore vectorStore;
    private final UserMemoryRepository userMemoryRepository;
    private final TokenCounter tokenCounter;
    private final boolean enabled;

    public MemoryRetrievalAdvisor(VectorStore vectorStore, UserMemoryRepository userMemoryRepository,
            TokenCounter tokenCounter, PromptProperties properties) {
        this.vectorStore = vectorStore;
        this.userMemoryRepository = userMemoryRepository;
        this.tokenCounter = tokenCounter;
        this.enabled = properties.memory().semantic();
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
        String userId = PromptAdvisor.contextString(request, ChatContextKeys.USER_ID);
        String query = PromptAdvisor.userText(request);
        if (userId == null || query == null) {
            return request;
        }

        try {
            List<Document> hits = searchMemories(userId, query);
            if (hits == null || hits.isEmpty()) {
                return request;
            }
            String memories = renderMemories(hits, pinnedFacts(userId));
            if (memories == null) {
                return request;
            }
            return request.mutate()
                    .prompt(request.prompt()
                            .augmentSystemMessage(existing -> new SystemMessage(existing.getText() + memories)))
                    .build();
        } catch (Exception e) {
            return request;
        }
    }

    private List<Document> searchMemories(String userId, String query) {
        return vectorStore.similaritySearch(
                SearchRequest.builder().query(query.trim()).topK(TOP_K).similarityThreshold(SIMILARITY_THRESHOLD)
                        .filterExpression(VectorFilters.userMemories(userId)).build());
    }

    /** Pinned memories are already in the system prompt (step 1). */
    private Set<String> pinnedFacts(String userId) {
        return userMemoryRepository.findByUserIdAndPinnedTrue(userId).stream()
                .map(memory -> memory.getMemoryFact().trim()).collect(Collectors.toSet());
    }

    private String renderMemories(List<Document> hits, Set<String> pinnedFacts) {
        StringBuilder text = new StringBuilder("\n\nRelevant User Memories:\n");
        int count = 0;
        int usedTokens = 0;
        for (Document hit : hits) {
            String fact = hit.getText() != null ? hit.getText().trim() : "";
            if (fact.isBlank() || pinnedFacts.contains(fact)) {
                continue;
            }
            int factTokens = tokenCounter.estimate(fact);
            if (usedTokens + factTokens > MAX_MEMORY_TOKENS && count > 0) {
                break;
            }
            text.append("- ").append(fact).append("\n");
            usedTokens += factTokens;
            count++;
        }
        return count == 0 ? null : text.toString();
    }
}
