package com.localmind.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.localmind.common.exception.UnauthorizedException;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit tests for {@link CurrentUserIdArgumentResolver}: userId claim over subject, fallbacks to the subject and
 * authentication name, and 401 without an authenticated user.
 */
class CurrentUserIdArgumentResolverTest {

    private final CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("The userId claim should win over the token subject")
    void userIdClaimWins() {
        authenticate(new JwtAuthenticationToken(jwt("subject-id", "claim-id")));

        assertEquals("claim-id", resolve());
    }

    @Test
    @DisplayName("A token without a userId claim should resolve to its subject")
    void fallsBackToSubject() {
        authenticate(new JwtAuthenticationToken(jwt("subject-id", null)));

        assertEquals("subject-id", resolve());
    }

    @Test
    @DisplayName("A blank userId claim should resolve to the token subject")
    void blankClaimFallsBackToSubject() {
        authenticate(new JwtAuthenticationToken(jwt("subject-id", " ")));

        assertEquals("subject-id", resolve());
    }

    @Test
    @DisplayName("A non-JWT authentication should resolve to the authentication name")
    void fallsBackToAuthenticationName() {
        authenticate(new TestingAuthenticationToken("named-user", "n/a"));

        assertEquals("named-user", resolve());
    }

    @Test
    @DisplayName("No authentication in the security context should be a 401")
    void missingAuthenticationIsUnauthorized() {
        UnauthorizedException error = assertThrows(UnauthorizedException.class, this::resolve);

        assertEquals("No authenticated user in security context", error.getMessage());
    }

    @Test
    @DisplayName("An authentication without a name should be a 401")
    void namelessAuthenticationIsUnauthorized() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("not-a-jwt");
        authenticate(authentication);

        UnauthorizedException error = assertThrows(UnauthorizedException.class, this::resolve);

        assertEquals("No authenticated user in security context", error.getMessage());
    }

    private String resolve() {
        return resolver.resolveArgument(null, null, null, null);
    }

    private static void authenticate(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Jwt jwt(String subject, String userId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (userId != null) {
            builder.claim(CurrentUserIdArgumentResolver.USER_ID_CLAIM, userId);
        }
        return builder.build();
    }
}
