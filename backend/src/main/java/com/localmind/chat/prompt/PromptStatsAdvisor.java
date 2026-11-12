package com.localmind.chat.prompt;

import com.localmind.chat.config.PromptProperties;
import com.localmind.llm.model.ModelProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

/**
 * Step 5: logs the token distribution of the final prompt and warns when it exceeds the model's context window. Never
 * modifies the request.
 */
@Slf4j
@Component
public class PromptStatsAdvisor implements PromptAdvisor {

    private static final int ORDER = 500;
    private static final int DEFAULT_NUM_CTX = 8192;

    private final TokenCounter tokenCounter;
    private final boolean enabled;

    public PromptStatsAdvisor(TokenCounter tokenCounter, PromptProperties properties) {
        this.tokenCounter = tokenCounter;
        this.enabled = properties.stats().enabled();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        if (!enabled || request == null || request.prompt() == null) {
            return request;
        }

        int systemTokens = 0;
        int userTokens = 0;
        int assistantTokens = 0;
        int otherTokens = 0;
        for (Message message : request.prompt().getInstructions()) {
            int count = tokenCounter.estimate(message.getText());
            switch (message.getMessageType()) {
                case SYSTEM -> systemTokens += count;
                case USER -> userTokens += count;
                case ASSISTANT -> assistantTokens += count;
                default -> otherTokens += count;
            }
        }

        int totalTokens = systemTokens + userTokens + assistantTokens + otherTokens;
        ModelProfile profile = PromptAdvisor.modelProfile(request);
        int numCtx = profile != null ? profile.numCtx() : DEFAULT_NUM_CTX;
        log.info("Prompt assembled: total={} tokens / ctx={} (system={}, history/assistant={}, user={}, other={})",
                totalTokens, numCtx, systemTokens, assistantTokens, userTokens, otherTokens);
        if (totalTokens > numCtx) {
            log.warn("Prompt token budget overflow! Total {} exceeds numCtx {}", totalTokens, numCtx);
        }
        return request;
    }
}
