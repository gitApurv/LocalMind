package com.localmind.llm.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.localmind.llm.config.LlmProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Verifies at startup that Ollama is recent enough. Older releases omit {@code capabilities} and
 * {@code details.context_length} from {@code /api/tags}; {@link ModelCatalogService} would then silently treat every
 * model as unable to call tools, list embedding models as chat models and fall back to the default context window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaVersionCheck {

    /** First release whose {@code /api/tags} reports model capabilities and context length. */
    static final String MINIMUM_VERSION = "0.30.0";

    /** Version reported by Ollama builds made from source. */
    private static final String DEVELOPMENT_VERSION = "0.0.0";

    private final RestClient shortTimeoutRestClient;
    private final LlmProperties llmProperties;

    @PostConstruct
    public void verify() {
        String version = fetchVersion();
        if (version == null || meetsMinimumVersion(version)) {
            return;
        }
        String message = "Ollama " + version + " at '" + llmProperties.ollamaBaseUrl()
                + "' is too old. LocalMind needs "
                + "Ollama " + MINIMUM_VERSION
                + " or later to detect which models support tools. Please upgrade Ollama.";
        if (llmProperties.validateModelsOnStartup()) {
            throw new IllegalStateException(message);
        }
        log.warn("{} Tools will not be offered for any model until then.", message);
    }

    /** Returns {@code null} when the version cannot be read; the model catalogue reports an unreachable Ollama. */
    private String fetchVersion() {
        try {
            OllamaVersionResponse response = shortTimeoutRestClient.get()
                    .uri(llmProperties.ollamaBaseUrl() + "/api/version").accept(MediaType.APPLICATION_JSON).retrieve()
                    .body(OllamaVersionResponse.class);
            String version = response != null ? response.version() : null;
            log.info("Connected to Ollama version {}", version);
            return version;
        } catch (Exception e) {
            log.debug("Could not read the Ollama version: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Compares {@code major.minor.patch}, ignoring a leading {@code v} and any pre-release or build suffix. Versions
     * that cannot be parsed and development builds pass, so an unusual version string never blocks startup.
     */
    static boolean meetsMinimumVersion(String version) {
        int[] actual = parse(version);
        if (actual == null || DEVELOPMENT_VERSION.equals(version.strip())) {
            return true;
        }
        int[] minimum = parse(MINIMUM_VERSION);
        for (int i = 0; i < minimum.length; i++) {
            if (actual[i] != minimum[i]) {
                return actual[i] > minimum[i];
            }
        }
        return true;
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String core = version.strip().replaceFirst("^v", "").split("[-+]", 2)[0];
        String[] parts = core.split("\\.");
        int[] numbers = new int[3];
        try {
            for (int i = 0; i < numbers.length && i < parts.length; i++) {
                numbers[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return numbers;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaVersionResponse(String version) {
    }
}
