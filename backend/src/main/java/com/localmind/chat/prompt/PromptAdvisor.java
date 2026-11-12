package com.localmind.chat.prompt;

import com.localmind.common.ai.ChatContextKeys;
import com.localmind.llm.model.ModelProfile;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;

/**
 * A step of LocalMind's prompt-assembly pipeline. All implementations are picked up by the chat orchestrator and run in
 * {@link #getOrder()} order. Advisors only transform the request; on failure they return it unchanged.
 */
public interface PromptAdvisor extends BaseAdvisor {

    @Override
    default ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    static String contextString(ChatClientRequest request, String key) {
        return ChatContextKeys.getString(request.context(), key);
    }

    static ModelProfile modelProfile(ChatClientRequest request) {
        return request.context() != null
                && request.context().get(ChatContextKeys.MODEL_PROFILE) instanceof ModelProfile profile
                        ? profile
                        : null;
    }

    /** Text of the current user message, or {@code null} when absent or blank. */
    static String userText(ChatClientRequest request) {
        Message userMessage = request.prompt() != null ? request.prompt().getUserMessage() : null;
        if (userMessage == null || userMessage.getText() == null || userMessage.getText().isBlank()) {
            return null;
        }
        return userMessage.getText();
    }
}
