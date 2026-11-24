package com.localmind.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localmind.auth.dto.LoginRequest;
import com.localmind.auth.dto.UpdateProfileRequest;
import com.localmind.auth.repository.RefreshTokenRepository;
import com.localmind.common.exception.UnauthorizedException;
import com.localmind.common.security.JwtProperties;
import com.localmind.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AuthService}: constant-time login for unknown emails and 401 for profile access by a deleted
 * user.
 */
class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthService authService = new AuthService(userRepository, mock(RefreshTokenRepository.class),
            passwordEncoder, mock(JwtService.class), new JwtProperties(null, null, false, null, null));

    @Test
    @DisplayName("login() with an unknown email should still check a password hash, so timing reveals nothing")
    void unknownEmailStillChecksAPassword() {
        when(userRepository.findByEmail("nobody@localmind.ai")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$dummy");

        assertThrows(UnauthorizedException.class,
                () -> authService.login(new LoginRequest("Nobody@localmind.ai", "password123")));

        verify(passwordEncoder).matches(eq("password123"), eq("$2a$10$dummy"));
    }

    @Test
    @DisplayName("getCurrentUser() for a user that no longer exists should be 401 (invalid session), not 404")
    void currentUserOfDeletedUserIsUnauthorized() {
        when(userRepository.findById("gone")).thenReturn(Optional.empty());

        UnauthorizedException error = assertThrows(UnauthorizedException.class,
                () -> authService.getCurrentUser("gone"));

        assertEquals("User not found", error.getMessage());
    }

    @Test
    @DisplayName("updateProfile() for a user that no longer exists should be 401 (invalid session), not 404")
    void updateProfileOfDeletedUserIsUnauthorized() {
        when(userRepository.findById("gone")).thenReturn(Optional.empty());

        UnauthorizedException error = assertThrows(UnauthorizedException.class,
                () -> authService.updateProfile("gone", new UpdateProfileRequest("Name", null)));

        assertEquals("User not found", error.getMessage());
    }
}
