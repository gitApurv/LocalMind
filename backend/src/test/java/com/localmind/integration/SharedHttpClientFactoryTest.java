package com.localmind.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.localmind.support.OfflineAiStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for the shared {@code RestClient.Builder}: it keeps Reactor Netty although httpclient5 is on the
 * classpath. httpclient5 is there for {@code WebScraperTools} only; Spring Boot would otherwise prefer it for the
 * builder that Spring AI's Ollama client is built from, and {@code application.yml} keeps Reactor Netty.
 */
@SpringBootTest
@OfflineAiStack
@ActiveProfiles("test")
class SharedHttpClientFactoryTest {

    @Autowired
    private ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder;

    @Test
    @DisplayName("The shared RestClient.Builder keeps Reactor Netty although httpclient5 is on the classpath")
    void sharedBuilderUsesReactorNetty() {
        assertThat(requestFactoryBuilder.build()).isInstanceOf(ReactorClientHttpRequestFactory.class);
    }
}
