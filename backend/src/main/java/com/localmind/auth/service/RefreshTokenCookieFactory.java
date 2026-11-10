package com.localmind.auth.service;

import com.localmind.common.security.JwtProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the HTTP-only cookie that carries the refresh token, scoped to the auth endpoints.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    private final JwtProperties jwtProperties;

    public ResponseCookie create(String rawRefreshToken) {
        return build(rawRefreshToken, jwtProperties.refreshTokenExpiration());
    }

    public ResponseCookie expire() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(jwtProperties.cookieSecure())
                .sameSite(jwtProperties.cookieSameSite()).path(COOKIE_PATH).maxAge(maxAge).build();
    }
}
