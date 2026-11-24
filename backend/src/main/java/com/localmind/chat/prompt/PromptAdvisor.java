package com.localmind.chat.prompt;

import com.localmind.common.ai.ChatContextKeys;
import com.localmind.llm.model.ModelProfile;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;

/**
 * One step of the prompt-assembly pipeline: adds one part of LocalMind's prompt to the chat request. All
 * implementations are picked up by the chat orchestrator and run in {@link #getOrder()} order. Advisors only transform
 * the request; on failure they return it unchanged.
 * <p>
 * The steps live in {@code advisor/}, history trimming in {@code history/}, token estimation in {@code token/}:
 * <ol>
 * <li>100 {@link com.localmind.chat.prompt.advisor.PromptStructureAdvisor}: system message</li>
 * <li>200 {@link com.localmind.chat.prompt.advisor.MemoryRetrievalAdvisor}: relevant memories</li>
 * <li>300 {@link com.localmind.chat.prompt.advisor.AutoRagAdvisor}: document excerpts</li>
 * <li>400 {@link com.localmind.chat.prompt.advisor.HistoryAdvisor}: summary and trimmed history</li>
 * <li>500 {@link com.localmind.chat.prompt.advisor.PromptStatsAdvisor}: token log</li>
 * </ol>
 */
public interface PromptAdvisor extends BaseAdvisor {

    ChatClientRequest apply(ChatClientRequest request, AdvisorChain chain);

    @Override
    default ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        try {
            return apply(request, chain);
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(getClass()).warn("Prompt advisor '{}' failed; continuing without its contribution",
                    getName(), e);
            return request;
        }
    }

    @Override
    default ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    static String contextString(ChatClientRequest request, String key) {
        return ChatContextKeys.getString(request.context(), key);
    }

    static ModelProfile budgetProfile(ChatClientRequest request) {
        ModelProfile profile = modelProfile(request);
        return profile != null ? profile : ModelProfile.generic(null, false);
    }

    static ModelProfile modelProfile(ChatClientRequest request) {
        return request.context() != null
                && request.context().get(ChatContextKeys.MODEL_PROFILE) instanceof ModelProfile profile
                        ? profile
                        : null;
    }

    static String userText(ChatClientRequest request) {
        Message userMessage = request.prompt() != null ? request.prompt().getUserMessage() : null;
        if (userMessage == null || userMessage.getText() == null || userMessage.getText().isBlank()) {
            return null;
        }
        return userMessage.getText();
    }
}
