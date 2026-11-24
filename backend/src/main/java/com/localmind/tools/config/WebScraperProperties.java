package com.localmind.tools.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings bound from {@code localmind.tools.scraper}: the SSRF-guard switch of the web page reader.
 *
 * @param allowPrivateSubnets
 *            disables the SSRF guard; only for local testing
 */
@ConfigurationProperties(prefix = "localmind.tools.scraper")
public record WebScraperProperties(boolean allowPrivateSubnets) {
}
