package com.localmind.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JwtProperties}: defaults for missing or blank values and trimming of configured ones.
 */
class JwtPropertiesTest {

    @Test
    @DisplayName("Missing values should fall back to the defaults")
    void missingValuesUseDefaults() {
        JwtProperties properties = new JwtProperties(null, null, false, null, null);

        assertEquals(Duration.ofMinutes(15), properties.accessTokenExpiration());
        assertEquals(Duration.ofDays(7), properties.refreshTokenExpiration());
        assertEquals("Strict", properties.cookieSameSite());
        assertEquals("./data/jwt-signing-key.pem", properties.keyFile());
    }

    @Test
    @DisplayName("Blank SameSite and key file (empty environment variables) should fall back to the defaults")
    void blankValuesUseDefaults() {
        JwtProperties properties = new JwtProperties(null, null, false, "", "  ");

        assertEquals("Strict", properties.cookieSameSite());
        assertEquals("./data/jwt-signing-key.pem", properties.keyFile());
    }

    @Test
    @DisplayName("Configured values should be kept, trimmed")
    void configuredValuesAreKept() {
        JwtProperties properties = new JwtProperties(null, null, true, " Lax ", "/keys/jwt.pem");

        assertEquals("Lax", properties.cookieSameSite());
        assertEquals("/keys/jwt.pem", properties.keyFile());
    }
}
