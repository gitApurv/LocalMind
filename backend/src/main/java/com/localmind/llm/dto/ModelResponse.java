package com.localmind.llm.dto;

import java.util.List;

public record ModelResponse(List<ModelInfo> models, String defaultModel) {
    public ModelResponse(List<ModelInfo> models) {
        this(models, null);
    }
}
