package com.localmind.llm.service;

import com.localmind.llm.model.ModelProfile;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Looks up the {@link ModelProfile} for a model name. Unknown models get a generic profile whose tool support is
 * inferred from the model family name.
 */
@Component
public class ModelProfileRegistry {

    private static final List<ModelProfile> KNOWN_PROFILES = List.of(ModelProfile.defaultProfile("gemma3:1b"),
            new ModelProfile("qwen2.5:7b", 16384, 2048, 4000, 8000, true),
            new ModelProfile("deepseek-r1:1.5b", 8192, 2048, 2000, 3000, false),
            new ModelProfile("deepseek-r1:8b", 16384, 4096, 4000, 6000, false));

    private static final List<String> TOOL_CAPABLE_HINTS = List.of("tools", "qwen", "llama", "mistral");

    public ModelProfile getProfile(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return KNOWN_PROFILES.getFirst();
        }
        String normalized = modelName.trim().toLowerCase(Locale.ROOT);
        return KNOWN_PROFILES.stream().filter(profile -> normalized.contains(profile.modelName())).findFirst()
                .orElseGet(() -> ModelProfile.generic(modelName,
                        TOOL_CAPABLE_HINTS.stream().anyMatch(normalized::contains)));
    }
}
