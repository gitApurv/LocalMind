package com.localmind.support;

import com.localmind.llm.service.ModelCatalogService;
import com.localmind.llm.service.ModelProfileRegistry;
import org.springframework.web.client.RestClient;

/**
 * {@link ModelCatalogService} that never calls Ollama: tool support comes from the profile registry only, so tests are
 * deterministic whether or not an Ollama instance runs on the machine.
 */
public class OfflineModelCatalog extends ModelCatalogService {

    private final ModelProfileRegistry registry;

    public OfflineModelCatalog(ModelProfileRegistry registry) {
        super(RestClient.create(), TestProperties.llmDefaults(), registry);
        this.registry = registry;
    }

    @Override
    public boolean supportsTools(String modelName) {
        return registry.getProfile(modelName).toolSupported();
    }
}
