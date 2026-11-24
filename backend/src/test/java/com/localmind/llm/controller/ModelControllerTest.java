package com.localmind.llm.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.localmind.common.security.CorsProperties;
import com.localmind.common.security.JwtProperties;
import com.localmind.common.security.SecurityConfig;
import com.localmind.llm.config.LlmProperties;
import com.localmind.llm.dto.ModelInfoResponse;
import com.localmind.llm.dto.ModelResponse;
import com.localmind.llm.service.ModelCatalogService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

/**
 * MockMvc tests for {@link ModelController}: 401 without authentication and the model list for an authenticated caller.
 * Runs as a {@code @WebMvcTest} slice with the real security configuration and a mocked catalogue.
 */
@WebMvcTest(ModelController.class)
@ActiveProfiles("test")
@Import(ModelControllerTest.TestConfig.class)
class ModelControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties({LlmProperties.class, JwtProperties.class, CorsProperties.class})
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean
        public RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        public ModelCatalogService modelCatalogService() {
            return mock(ModelCatalogService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ModelCatalogService modelCatalogService;

    @Test
    @DisplayName("GET /api/models unauthenticated should return 401 Unauthorized")
    void listModelsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/models")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/models authenticated should return available models list")
    void listModelsAuthenticated() throws Exception {
        when(modelCatalogService.listAvailableModels()).thenReturn(
                new ModelResponse(List.of(new ModelInfoResponse("test-model", "test-model", 100L, "2026-01-01", true)),
                        "test-model"));

        mockMvc.perform(
                get("/api/models").with(jwt().jwt(jwt -> jwt.subject("user-1").claim("email", "user@example.com"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.models", notNullValue()))
                .andExpect(jsonPath("$.models", hasSize(1)))
                .andExpect(jsonPath("$.models[0].name", is("test-model")))
                .andExpect(jsonPath("$.models[0].supportsTools", is(true)))
                .andExpect(jsonPath("$.defaultModel", is("test-model")));
    }
}
