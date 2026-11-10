package com.localmind.common.security;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param keyFile
 *            PKCS#8 PEM file holding the RSA key that signs access tokens; created on first start
 */
@ConfigurationProperties(prefix = "localmind.jwt")
public record JwtProperties(Duration accessTokenExpiration, Duration refreshTokenExpiration, boolean cookieSecure,
        String cookieSameSite, String keyFile) {

    public JwtProperties {
        accessTokenExpiration = Objects.requireNonNullElse(accessTokenExpiration, Duration.ofMinutes(15));
        refreshTokenExpiration = Objects.requireNonNullElse(refreshTokenExpiration, Duration.ofDays(7));
        cookieSameSite = Objects.requireNonNullElse(cookieSameSite, "Strict");
        keyFile = Objects.requireNonNullElse(keyFile, "./data/jwt-signing-key.pem");
    }
}
