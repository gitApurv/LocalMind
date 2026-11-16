package com.localmind.llm.service;

import com.localmind.llm.model.ModelProfile;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Registry of active {@link ModelProfile}s, populated from Ollama metadata on startup. There are no hardcoded profiles
 * or capability heuristics.
 */
@Component
public class ModelProfileRegistry {

    private static final String DEFAULT_PROFILE_NAME = "default";
    private static final String LATEST_TAG = ":latest";

    private final Map<String, ModelProfile> profiles = new ConcurrentHashMap<>();

    public void registerProfile(ModelProfile profile) {
        if (profile == null || profile.modelName() == null || profile.modelName().isBlank()) {
            return;
        }
        String key = normalize(profile.modelName());
        profiles.put(key, profile);
    }

    /**
     * The profile registered for {@code modelName}, or for its {@code :latest} tag when no tag is given (as Ollama
     * resolves names). Unknown or blank names get the generic profile, without tool support.
     */
    public ModelProfile getProfile(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return ModelProfile.generic(DEFAULT_PROFILE_NAME, false);
        }
        String normalized = normalize(modelName);
        ModelProfile profile = profiles.get(normalized);
        if (profile == null && !normalized.contains(":")) {
            profile = profiles.get(normalized + LATEST_TAG);
        }
        return profile != null ? profile : ModelProfile.generic(modelName.trim(), false);
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
