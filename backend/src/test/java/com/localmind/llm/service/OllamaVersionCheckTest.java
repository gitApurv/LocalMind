package com.localmind.llm.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.localmind.llm.config.LlmProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link OllamaVersionCheck}: minimum-version comparison, failing or only warning on an old Ollama
 * depending on model validation, and skipping an unreachable Ollama.
 */
class OllamaVersionCheckTest {

    private static final String BASE_URL = "http://localhost:11434";

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"0.30.0, true", "0.30.1, true", "0.40.16, true", "1.0.0, true", "v0.31.0, true", "0.30.0-rc1, true",
            "0.24.3, false", "0.24.0, false", "0.6.2, false", "v0.12.11, false", "0.29, false",
            "0.0.0, true", "not-a-version, true"})
    @DisplayName("meetsMinimumVersion() compares major.minor.patch against 0.30.0")
    void meetsMinimumVersion(String version, boolean expected) {
        assertEquals(expected, OllamaVersionCheck.meetsMinimumVersion(version));
    }

    @Test
    @DisplayName("verify() fails startup for an old Ollama when model validation is enabled")
    void oldVersionFailsWhenValidating() {
        OllamaVersionCheck check = checkRespondingWith("0.24.3", true);

        IllegalStateException e = assertThrows(IllegalStateException.class, check::verify);
        assertTrue(e.getMessage().contains("0.24.3"));
        assertTrue(e.getMessage().contains(OllamaVersionCheck.MINIMUM_VERSION));
    }

    @Test
    @DisplayName("verify() only warns for an old Ollama when model validation is disabled")
    void oldVersionWarnsWhenNotValidating() {
        OllamaVersionCheck check = checkRespondingWith("0.24.3", false);

        assertDoesNotThrow(check::verify);
    }

    @Test
    @DisplayName("verify() accepts a supported Ollama version")
    void supportedVersionPasses() {
        OllamaVersionCheck check = checkRespondingWith("0.30.0", true);

        assertDoesNotThrow(check::verify);
    }

    @Test
    @DisplayName("verify() leaves an unreachable or failing Ollama to the model catalogue")
    void unreadableVersionIsSkipped() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/api/version")).andRespond(withServerError());
        OllamaVersionCheck check = new OllamaVersionCheck(builder.build(), properties(true));

        assertDoesNotThrow(check::verify);
        server.verify();
    }

    private static OllamaVersionCheck checkRespondingWith(String version, boolean validate) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/api/version")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"version\":\"" + version + "\"}", MediaType.APPLICATION_JSON));
        return new OllamaVersionCheck(builder.build(), properties(validate));
    }

    private static LlmProperties properties(boolean validate) {
        return new LlmProperties(null, null, BASE_URL, validate);
    }
}
