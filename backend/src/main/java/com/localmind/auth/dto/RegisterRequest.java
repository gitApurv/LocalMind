package com.localmind.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Limits match the {@code users} columns; BCrypt ignores password bytes beyond 72, so longer passwords are rejected.
 */
public record RegisterRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") @Size(max = 255, message = "Email cannot exceed 255 characters") String email,
        @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") String password,
        @NotBlank(message = "Full name is required") @Size(max = 100, message = "Full name cannot exceed 100 characters") String fullName) {
}
