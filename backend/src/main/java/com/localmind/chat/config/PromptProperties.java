package com.localmind.chat.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings bound from {@code localmind.prompt}: feature switches for the prompt-assembly advisors. Everything is
 * enabled by default.
 */
@ConfigurationProperties(prefix = "localmind.prompt")
public record PromptProperties(Rag rag, Memory memory, History history, Stats stats) {

    public PromptProperties {
        rag = Objects.requireNonNullElse(rag, new Rag(true));
        memory = Objects.requireNonNullElse(memory, new Memory(true));
        history = Objects.requireNonNullElse(history, new History(true));
        stats = Objects.requireNonNullElse(stats, new Stats(true));
    }

    public record Rag(@DefaultValue("true") boolean auto) {
    }

    public record Memory(@DefaultValue("true") boolean semantic) {
    }

    public record History(@DefaultValue("true") boolean tokenTrim) {
    }

    public record Stats(@DefaultValue("true") boolean enabled) {
    }
}
