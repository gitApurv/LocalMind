package com.localmind.auth.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100, message = "Full name cannot exceed 100 characters") String fullName,
        @Size(max = 8000, message = "Custom instructions cannot exceed 8000 characters") String customInstructions) {
}
