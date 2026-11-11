package com.localmind.tools.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.localmind.tools.config.SearxngProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WebSearchToolsTest {

    private WebSearchTools webSearchTools;
    private MockRestServiceServer mockServer;
    private final String baseUrl = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        webSearchTools = new WebSearchTools(restClientBuilder.build(), new SearxngProperties(baseUrl));
    }

    @Test
    @DisplayName("searchWeb() should query SearXNG and parse top results into structured search results")
    void searchWebSuccess() {
        String mockSearxngResponse = """
                {
                  "query": "spring ai",
                  "results": [
                    {
                      "title": "Spring AI Documentation",
                      "url": "https://docs.spring.io/spring-ai",
                      "content": "Spring AI is an application framework for AI engineering."
                    },
                    {
                      "title": "Spring AI GitHub",
                      "url": "https://github.com/spring-projects/spring-ai",
                      "content": "Official repository for Spring AI."
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:8081/search?q=spring%20ai&format=json"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockSearxngResponse, MediaType.APPLICATION_JSON));

        List<WebSearchTools.SearchResult> results = webSearchTools.searchWeb("spring ai");
        mockServer.verify();

        assertEquals(2, results.size());
        assertEquals("Spring AI Documentation", results.get(0).title());
        assertEquals("https://docs.spring.io/spring-ai", results.get(0).url());
        assertEquals("Spring AI is an application framework for AI engineering.", results.get(0).snippet());
    }

    @Test
    @DisplayName("searchWeb() with blank query should return empty list without HTTP call")
    void searchWebBlankQuery() {
        List<WebSearchTools.SearchResult> results = webSearchTools.searchWeb("   ");
        assertTrue(results.isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("searchWeb() when SearXNG returns server error should return empty list gracefully")
    void searchWebServerErrorHandled() {
        mockServer.expect(requestTo("http://localhost:8081/search?q=error%20test&format=json"))
                .andExpect(method(HttpMethod.GET)).andRespond(withServerError());

        List<WebSearchTools.SearchResult> results = webSearchTools.searchWeb("error test");
        mockServer.verify();

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("FunctionCallback invocation for WebSearchTools should return formatted text results")
    void functionCallbackExecution() {
        String mockSearxngResponse = """
                {
                  "query": "local ai",
                  "results": [
                    {
                      "title": "Ollama",
                      "url": "https://ollama.com",
                      "content": "Get up and running with large language models locally."
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:8081/search?q=local%20ai&format=json"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockSearxngResponse, MediaType.APPLICATION_JSON));

        var callback = webSearchTools.toolCallback();
        assertNotNull(callback);
        assertEquals("searchWeb", callback.getToolDefinition().name());

        String response = callback.call("{\"query\": \"local ai\"}");
        mockServer.verify();

        assertNotNull(response);
        assertTrue(response.contains("Ollama"));
        assertTrue(response.contains("https://ollama.com"));
    }
}
