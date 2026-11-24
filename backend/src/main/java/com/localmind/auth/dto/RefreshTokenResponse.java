package com.localmind.auth.dto;

/**
 * Response body of {@code POST /api/auth/refresh}: a new bearer access token. The rotated refresh token travels in an
 * HTTP-only cookie, not in this body.
 *
 * @param tokenType
 *            always {@code Bearer}
 * @param expiresIn
 *            lifetime of the access token in seconds
 */
public record RefreshTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
