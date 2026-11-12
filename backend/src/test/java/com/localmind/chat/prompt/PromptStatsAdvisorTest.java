package com.localmind.chat.prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localmind.chat.config.PromptProperties;
import com.localmind.llm.model.ModelProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

class PromptStatsAdvisorTest {
    private final TokenCounter tokenCounter = new CharacterTokenCounter();
    private final PromptStatsAdvisor advisor = new PromptStatsAdvisor(tokenCounter, PromptProperties.defaults());

    @Test
    void beforeComputesAndLogsPromptTokens() {
        Prompt prompt = new Prompt(List.of(new SystemMessage("You are a helpful assistant."),
                new UserMessage("What is the capital of France?")));
        ModelProfile profile = ModelProfile.defaultProfile("gemma3:1b");
        ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).context(Map.of("modelProfile", profile))
                .build();

        AdvisorChain chain = mock(AdvisorChain.class);
        ChatClientRequest modified = advisor.before(request, chain);

        assertNotNull(modified);
        assertEquals(500, advisor.getOrder());
    }
}
