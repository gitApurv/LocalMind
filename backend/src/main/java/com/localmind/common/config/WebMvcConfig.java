package com.localmind.common.config;

import com.localmind.common.security.CurrentUserIdArgumentResolver;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** Long enough for slow local models to finish streaming a response. */
    private static final Duration ASYNC_REQUEST_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration HTTP_CLIENT_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(ASYNC_REQUEST_TIMEOUT.toMillis());
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver());
    }

    /**
     * Client for LocalMind's own short HTTP calls (Ollama model list, SearXNG) with tight timeouts. The shared
     * {@code RestClient.Builder} is cloned, never customized: Spring AI builds its Ollama client from that builder, and
     * embedding or completion calls on slow hardware need far longer than these timeouts. Declaring a
     * {@code RestClient.Builder} bean here would replace Spring Boot's and break that separation.
     */
    @Bean
    public RestClient shortTimeoutRestClient(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(HTTP_CLIENT_TIMEOUT);
        factory.setReadTimeout(HTTP_CLIENT_TIMEOUT);
        return restClientBuilder.clone().requestFactory(factory).build();
    }
}
