package com.localmind.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.localmind.common.security.JwtProperties;
import com.localmind.user.entity.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Unit tests for {@link JwtService}: access tokens carry the user's id, email and name claims with the configured
 * lifetime.
 */
class JwtServiceTest {

    @Test
    @DisplayName("generateAccessToken() should sign the user's id, email and name with the configured lifetime")
    void accessTokenCarriesUserClaims() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("test").generate();
        JwtService jwtService = new JwtService(new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key))),
                new JwtProperties(Duration.ofMinutes(15), null, false, null, null));
        User user = User.builder().id("u1").email("ada@localmind.ai").build();

        Jwt jwt = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build()
                .decode(jwtService.generateAccessToken(user));

        assertEquals("localmind", jwt.getClaimAsString("iss"));
        assertEquals("u1", jwt.getSubject());
        assertEquals("u1", jwt.getClaimAsString("userId"));
        assertEquals("ada@localmind.ai", jwt.getClaimAsString("email"));
        assertEquals("", jwt.getClaimAsString("fullName"));
        assertTrue(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).equals(Duration.ofMinutes(15)));
    }
}
