package com.localmind.auth.dto;

import com.localmind.user.entity.User;
import java.time.Instant;

/**
 * Response body for the caller's profile, returned by {@code GET /api/auth/me}, {@code PATCH /api/auth/me} and {@code
 * POST /api/auth/register}, and embedded in {@link AuthResponse}.
 */
public record UserSummary(String id, String email, String fullName, String customInstructions, Instant createdAt) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.getCustomInstructions(),
                user.getCreatedAt());
    }
}
