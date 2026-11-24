package com.localmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the LocalMind backend. {@code @ConfigurationPropertiesScan} binds every {@code localmind.*} properties
 * record, {@code @EnableAsync} runs the {@code @Async} jobs (document ingestion, conversation titles and summaries) and
 * {@code @EnableScheduling} runs the hourly refresh-token cleanup.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class LocalMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalMindApplication.class, args);
    }
}
