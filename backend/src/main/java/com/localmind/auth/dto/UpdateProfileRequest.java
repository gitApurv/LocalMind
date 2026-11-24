package com.localmind.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body of {@code PATCH /api/auth/me}: optional new full name and custom instructions. A null or blank full name
 * keeps the current one; null custom instructions keep the current ones, blank ones clear them.
 */
public record UpdateProfileRequest(
        @Size(max = 100, message = "Full name cannot exceed 100 characters") String fullName,
        @Size(max = 8000, message = "Custom instructions cannot exceed 8000 characters") String customInstructions) {
}
