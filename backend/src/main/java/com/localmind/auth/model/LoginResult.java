package com.localmind.auth.model;

import com.localmind.auth.dto.AuthResponse;

/**
 * Login outcome: the response body plus the raw refresh token destined for an HTTP-only cookie.
 */
public record LoginResult(AuthResponse authResponse, String rawRefreshToken) {
}
