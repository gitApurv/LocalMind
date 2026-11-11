package com.localmind.tools.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowPrivateSubnets
 *            disables the SSRF guard; only for local testing
 */
@ConfigurationProperties(prefix = "localmind.tools.scraper")
public record WebScraperProperties(boolean allowPrivateSubnets) {
}
