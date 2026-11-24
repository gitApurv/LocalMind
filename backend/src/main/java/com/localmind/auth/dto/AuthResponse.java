package com.localmind.auth.dto;

/**
 * Response body of {@code POST /api/auth/login}: a bearer access token and the signed-in user. The refresh token
 * travels in an HTTP-only cookie, not in this body.
 *
 * @param tokenType
 *            always {@code Bearer}
 * @param expiresIn
 *            lifetime of the access token in seconds
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {
}
