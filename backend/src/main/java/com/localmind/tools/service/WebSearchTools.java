package com.localmind.tools.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.localmind.tools.config.SearxngProperties;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Web search through a self-hosted SearXNG instance.
 */
@Slf4j
@Component
public class WebSearchTools {

    private static final String NAME = "searchWeb";
    private static final String DESCRIPTION = "Search the web for current, up-to-date real-time information via "
            + "privacy-preserving search engine. Returns top results with titles, URLs, and summaries.";
    private static final int MAX_RESULTS = 5;

    private final RestClient restClient;
    private final SearxngProperties properties;

    public record WebSearchRequest(@JsonProperty(value = "query", required = true) String query) {
    }

    record SearchResult(String title, String url, String snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearxngResponse(List<SearxngItem> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearxngItem(String title, String url, String content) {
    }

    public WebSearchTools(RestClient shortTimeoutRestClient, SearxngProperties properties) {
        this.restClient = shortTimeoutRestClient;
        this.properties = properties;
    }

    List<SearchResult> searchWeb(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            URI searchUri = UriComponentsBuilder.fromUriString(properties.baseUrl() + "/search")
                    .queryParam("q", query.trim()).queryParam("format", "json").build().toUri();
            SearxngResponse response = restClient.get().uri(searchUri).accept(MediaType.APPLICATION_JSON).retrieve()
                    .body(SearxngResponse.class);
            if (response == null || response.results() == null) {
                return List.of();
            }
            return response.results().stream().limit(MAX_RESULTS)
                    .map(item -> new SearchResult(Objects.requireNonNullElse(item.title(), ""),
                            Objects.requireNonNullElse(item.url(), ""), Objects.requireNonNullElse(item.content(), "")))
                    .toList();
        } catch (Exception e) {
            log.warn("Web search query failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    String formatResults(String query) {
        List<SearchResult> results = searchWeb(query);
        if (results.isEmpty()) {
            return "No web search results found for query: " + Objects.requireNonNullElse(query, "");
        }
        StringBuilder text = new StringBuilder("Web Search Results for '").append(query).append("':\n\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            text.append(i + 1).append(". ").append(result.title()).append("\n").append("   URL: ").append(result.url())
                    .append("\n").append("   Snippet: ").append(result.snippet()).append("\n\n");
        }
        return text.toString().trim();
    }

    public ToolCallback toolCallback() {
        return FunctionToolCallback
                .builder(NAME, (WebSearchRequest request) -> formatResults(request != null ? request.query() : null))
                .description(DESCRIPTION).inputType(WebSearchRequest.class).build();
    }
}
