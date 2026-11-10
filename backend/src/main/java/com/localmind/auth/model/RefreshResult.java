package com.localmind.auth.model;

import com.localmind.auth.dto.RefreshTokenResponse;

/**
 * Refresh outcome: the response body plus the rotated raw refresh token destined for an HTTP-only cookie.
 */
public record RefreshResult(RefreshTokenResponse tokenResponse, String rawRefreshToken) {
}
