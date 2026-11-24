package com.localmind.chat.prompt.advisor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localmind.chat.prompt.token.CharacterTokenCounter;
import com.localmind.chat.prompt.token.TokenCounter;
import com.localmind.llm.model.ModelProfile;
import com.localmind.support.TestProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Unit tests for {@link PromptStatsAdvisor}: prompt token counting at order 500 and the unchanged request when counting
 * fails.
 */
class PromptStatsAdvisorTest {
    private final TokenCounter tokenCounter = new CharacterTokenCounter();
    private final PromptStatsAdvisor advisor = new PromptStatsAdvisor(tokenCounter, TestProperties.promptDefaults());

    @Test
    void beforeComputesAndLogsPromptTokens() {
        Prompt prompt = new Prompt(List.of(new SystemMessage("You are a helpful assistant."),
                new UserMessage("What is the capital of France?")));
        ModelProfile profile = ModelProfile.generic("test-model", false);
        ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).context(Map.of("modelProfile", profile))
                .build();

        AdvisorChain chain = mock(AdvisorChain.class);
        ChatClientRequest modified = advisor.before(request, chain);

        assertNotNull(modified);
        assertEquals(500, advisor.getOrder());
    }

    @Test
    void returnsRequestUnchangedWhenCountingFails() {
        PromptStatsAdvisor failing = new PromptStatsAdvisor(text -> {
            throw new IllegalStateException("tokenizer failure");
        }, TestProperties.promptDefaults());
        ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt("Hello")).context(Map.of()).build();

        assertSame(request, failing.before(request, mock(AdvisorChain.class)));
    }
}
