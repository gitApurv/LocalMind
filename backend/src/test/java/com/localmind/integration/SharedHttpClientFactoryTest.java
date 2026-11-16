package com.localmind.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

/**
 * httpclient5 is on the classpath for {@code WebScraperTools} only. Spring Boot would otherwise prefer it for the
 * shared {@code RestClient.Builder} that Spring AI's Ollama client is built from; {@code application.yml} keeps Reactor
 * Netty.
 */
@SpringBootTest
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
