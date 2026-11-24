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

/**
 * Spring configuration for Spring MVC: the async request timeout for streamed responses, the
 * {@link CurrentUserIdArgumentResolver} and the {@code shortTimeoutRestClient} bean for LocalMind's own short HTTP
 * calls.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

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

    @Bean
    public RestClient shortTimeoutRestClient(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(HTTP_CLIENT_TIMEOUT);
        factory.setReadTimeout(HTTP_CLIENT_TIMEOUT);
        return restClientBuilder.clone().requestFactory(factory).build();
    }
}
