package com.localmind.auth.model;

import com.localmind.auth.dto.RefreshTokenResponse;

/**
 * Outcome of a refresh: the response body plus the rotated raw refresh token destined for an HTTP-only cookie.
 */
public record RefreshResult(RefreshTokenResponse tokenResponse, String rawRefreshToken) {
}
