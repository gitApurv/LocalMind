package com.localmind.auth.dto;

import com.localmind.user.entity.User;
import java.time.Instant;

public record UserSummary(String id, String email, String fullName, String customInstructions, Instant createdAt) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.getCustomInstructions(),
                user.getCreatedAt());
    }
}
