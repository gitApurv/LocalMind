package com.localmind.chat.prompt.advisor;

import com.localmind.chat.config.PromptProperties;
import com.localmind.chat.prompt.PromptAdvisor;
import com.localmind.chat.prompt.token.TokenCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

/**
 * Prompt step 5 (order 500): logs the token distribution of the final prompt and warns when it exceeds the model's
 * context window. Never modifies the request. Switched off by {@code localmind.prompt.stats.enabled}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptStatsAdvisor implements PromptAdvisor {

    private static final int ORDER = 500;

    private final TokenCounter tokenCounter;
    private final PromptProperties properties;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientRequest apply(ChatClientRequest request, AdvisorChain chain) {
        if (!properties.stats().enabled() || request == null || request.prompt() == null) {
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
        int numCtx = PromptAdvisor.budgetProfile(request).numCtx();
        log.info("Prompt assembled: total={} tokens / ctx={} (system={}, history/assistant={}, user={}, other={})",
                totalTokens, numCtx, systemTokens, assistantTokens, userTokens, otherTokens);
        if (totalTokens > numCtx) {
            log.warn("Prompt token budget overflow! Total {} exceeds numCtx {}", totalTokens, numCtx);
        }
        return request;
    }
}
