package com.localmind.support;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import reactor.core.publisher.Flux;

/**
 * Test support: a {@link ChatModel} with a canned reply for {@code call} and canned chunks for {@code stream}, which
 * records the last prompt it received, so tests need no Ollama.
 */
public final class StubChatModel implements ChatModel {

    private String reply = "test";
    private Flux<ChatResponse> streamResponses = Flux.empty();
    private Prompt lastPrompt;

    public void reply(String text) {
        reply = text;
    }

    public void streamResponses(Flux<ChatResponse> responses) {
        streamResponses = responses;
    }

    public Prompt lastPrompt() {
        return lastPrompt;
    }

    @Override
    public ChatOptions getOptions() {
        return OllamaChatOptions.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        lastPrompt = prompt;
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        lastPrompt = prompt;
        return streamResponses;
    }
}
