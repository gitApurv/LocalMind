package com.localmind.llm.service;

import com.localmind.llm.model.ModelNames;
import com.localmind.llm.model.ModelProfile;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Holds the active {@link ModelProfile}s, registered by {@link ModelCatalogService} from Ollama metadata whenever the
 * catalogue loads. There are no hardcoded profiles or capability heuristics.
 */
@Component
public class ModelProfileRegistry {

    private static final String DEFAULT_PROFILE_NAME = "default";

    private final Map<String, ModelProfile> profiles = new ConcurrentHashMap<>();

    public void registerProfile(ModelProfile profile) {
        if (profile == null || profile.modelName() == null || profile.modelName().isBlank()) {
            return;
        }
        profiles.put(ModelNames.canonical(profile.modelName()), profile);
    }

    public ModelProfile getProfile(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return ModelProfile.generic(DEFAULT_PROFILE_NAME, false);
        }
        ModelProfile profile = profiles.get(ModelNames.canonical(modelName));
        return profile != null ? profile : ModelProfile.generic(modelName.trim(), false);
    }
}
