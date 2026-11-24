package com.localmind.common.security;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings bound from {@code localmind.jwt}: token lifetimes, refresh-cookie flags and the signing-key file. Defaults
 * are 15-minute access tokens, 7-day refresh tokens and SameSite {@code Strict}. Blank strings fall back to the
 * defaults like missing ones, so an empty environment variable ({@code JWT_KEY_FILE=}) cannot produce an empty SameSite
 * attribute or a key file path pointing at the working directory.
 *
 * @param keyFile
 *            PKCS#8 PEM file holding the RSA key that signs access tokens; created on first start
 */
@ConfigurationProperties(prefix = "localmind.jwt")
public record JwtProperties(Duration accessTokenExpiration, Duration refreshTokenExpiration, boolean cookieSecure,
        String cookieSameSite, String keyFile) {

    private static final String DEFAULT_COOKIE_SAME_SITE = "Strict";
    private static final String DEFAULT_KEY_FILE = "./data/jwt-signing-key.pem";

    public JwtProperties {
        accessTokenExpiration = Objects.requireNonNullElse(accessTokenExpiration, Duration.ofMinutes(15));
        refreshTokenExpiration = Objects.requireNonNullElse(refreshTokenExpiration, Duration.ofDays(7));
        cookieSameSite = isBlank(cookieSameSite) ? DEFAULT_COOKIE_SAME_SITE : cookieSameSite.trim();
        keyFile = isBlank(keyFile) ? DEFAULT_KEY_FILE : keyFile.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
