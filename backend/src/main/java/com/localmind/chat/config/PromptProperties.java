package com.localmind.chat.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Feature switches for the prompt-assembly advisors. Everything is enabled by default.
 */
@ConfigurationProperties(prefix = "localmind.prompt")
public record PromptProperties(Rag rag, Memory memory, History history, Stats stats) {

    public PromptProperties {
        rag = Objects.requireNonNullElse(rag, new Rag(true));
        memory = Objects.requireNonNullElse(memory, new Memory(true));
        history = Objects.requireNonNullElse(history, new History(true));
        stats = Objects.requireNonNullElse(stats, new Stats(true));
    }

    public static PromptProperties defaults() {
        return new PromptProperties(null, null, null, null);
    }

    /**
     * Automatically inject document excerpts for conversations with attachments.
     */
    public record Rag(@DefaultValue("true") boolean auto) {
    }

    /** Inject semantically relevant user memories into the system prompt. */
    public record Memory(@DefaultValue("true") boolean semantic) {
    }

    /** Trim history to the model's token budget. */
    public record History(@DefaultValue("true") boolean tokenTrim) {
    }

    /** Log per-role token statistics of the assembled prompt. */
    public record Stats(@DefaultValue("true") boolean enabled) {
    }
}
