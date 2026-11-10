package com.localmind.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") @Size(max = 255, message = "Email cannot exceed 255 characters") String email,
        @NotBlank(message = "Password is required") @Size(max = 72, message = "Password cannot exceed 72 characters") String password) {
}
