package com.localmind.llm.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.localmind.common.security.CorsProperties;
import com.localmind.common.security.JwtProperties;
import com.localmind.common.security.SecurityConfig;
import com.localmind.llm.config.LlmProperties;
import com.localmind.llm.service.ModelCatalogService;
import com.localmind.llm.service.ModelProfileRegistry;
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

@WebMvcTest(ModelController.class)
@ActiveProfiles("test")
@Import(ModelControllerTest.TestConfig.class)
class ModelControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties({LlmProperties.class, JwtProperties.class, CorsProperties.class})
    @Import({ModelCatalogService.class, ModelProfileRegistry.class, SecurityConfig.class})
    static class TestConfig {
        /** The slice has no RestClient auto-configuration; WebMvcConfig derives its client from this builder. */
        @Bean
        public RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/models unauthenticated should return 401 Unauthorized")
    void listModelsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/models")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/models authenticated should return available models list")
    void listModelsAuthenticated() throws Exception {
        mockMvc.perform(
                get("/api/models").with(jwt().jwt(jwt -> jwt.subject("user-1").claim("email", "user@example.com"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.models", notNullValue()))
                .andExpect(jsonPath("$.models", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.models[0].supportsTools", notNullValue()));
    }
}
