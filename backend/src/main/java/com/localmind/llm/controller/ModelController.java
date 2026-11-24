package com.localmind.llm.controller;

import com.localmind.llm.dto.ModelResponse;
import com.localmind.llm.service.ModelCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for the installed chat model catalogue under {@code /api/models}. */
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelCatalogService modelCatalogService;

    @GetMapping
    public ModelResponse listAvailableModels() {
        return modelCatalogService.listAvailableModels();
    }
}
