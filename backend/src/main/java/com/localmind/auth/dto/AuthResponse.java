package com.localmind.auth.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {
}
