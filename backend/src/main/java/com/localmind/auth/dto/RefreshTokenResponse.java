package com.localmind.auth.dto;

public record RefreshTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
