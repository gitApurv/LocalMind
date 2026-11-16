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

/** Web search through a self-hosted SearXNG instance. */
@Slf4j
@Component
public class WebSearchTools {

    private static final String NAME = "searchWeb";
    private static final String DESCRIPTION = "Search the web for current, up-to-date real-time information via "
            + "privacy-preserving search engine. Returns top results with titles, URLs, and summaries.";
    private static final int MAX_RESULTS = 5;
    private static final String UNAVAILABLE = "Error: Web search is unavailable right now. Answer without web results "
            + "and tell the user that search could not be reached.";

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

    /** Throws when SearXNG cannot be reached or answers with an error, so callers can tell that from no results. */
    List<SearchResult> searchWeb(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String searchUrl = properties.baseUrl() + "/search";
        URI searchUri = UriComponentsBuilder.fromUriString(searchUrl).queryParam("q", query.trim())
                .queryParam("format", "json").build().toUri();
        SearxngResponse response = restClient.get().uri(searchUri).accept(MediaType.APPLICATION_JSON).retrieve()
                .body(SearxngResponse.class);
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream().limit(MAX_RESULTS).map(WebSearchTools::toSearchResult).toList();
    }

    private static SearchResult toSearchResult(SearxngItem item) {
        String title = Objects.requireNonNullElse(item.title(), "");
        String url = Objects.requireNonNullElse(item.url(), "");
        String snippet = Objects.requireNonNullElse(item.content(), "");
        return new SearchResult(title, url, snippet);
    }

    String formatResults(String query) {
        if (query == null || query.isBlank()) {
            return "Error: Search query must not be blank.";
        }
        String trimmed = query.trim();
        List<SearchResult> results;
        try {
            results = searchWeb(trimmed);
        } catch (RuntimeException e) {
            log.warn("Web search query failed for '{}': {}", trimmed, e.getMessage());
            return UNAVAILABLE;
        }
        if (results.isEmpty()) {
            return "No web search results found for query: " + trimmed;
        }
        StringBuilder text = new StringBuilder("Web Search Results for '").append(trimmed).append("':\n\n");
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
