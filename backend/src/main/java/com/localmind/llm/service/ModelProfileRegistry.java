package com.localmind.llm.service;

import com.localmind.llm.model.ModelProfile;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Registry of active {@link ModelProfile}s dynamically populated from Ollama metadata on server startup. Contains no
 * hardcoded model profiles or static capability heuristics.
 */
@Component
public class ModelProfileRegistry {

    private final Map<String, ModelProfile> profiles = new ConcurrentHashMap<>();

    public void registerProfile(ModelProfile profile) {
        if (profile == null || profile.modelName() == null || profile.modelName().isBlank()) {
            return;
        }
        profiles.put(normalize(profile.modelName()), profile);
    }

    public void registerProfiles(Collection<ModelProfile> newProfiles) {
        if (newProfiles != null) {
            newProfiles.forEach(this::registerProfile);
        }
    }

    public ModelProfile getProfile(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return profiles.values().stream().findFirst().orElseGet(() -> ModelProfile.generic("default", false));
        }
        String normalized = normalize(modelName);
        ModelProfile profile = profiles.get(normalized);
        if (profile != null) {
            return profile;
        }
        for (Map.Entry<String, ModelProfile> entry : profiles.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(normalized + ":") || normalized.startsWith(key + ":")) {
                return entry.getValue();
            }
        }
        return ModelProfile.generic(modelName.trim(), false);
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
