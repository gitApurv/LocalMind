package com.localmind.auth.controller;

import com.localmind.auth.dto.AuthResponse;
import com.localmind.auth.dto.LoginRequest;
import com.localmind.auth.dto.RefreshTokenResponse;
import com.localmind.auth.dto.RegisterRequest;
import com.localmind.auth.dto.UpdateProfileRequest;
import com.localmind.auth.dto.UserSummary;
import com.localmind.auth.model.LoginResult;
import com.localmind.auth.model.RefreshResult;
import com.localmind.auth.service.AuthService;
import com.localmind.auth.service.RefreshTokenCookieFactory;
import com.localmind.common.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;

    @GetMapping("/me")
    public UserSummary getCurrentUser(@CurrentUserId String userId) {
        return authService.getCurrentUser(userId);
    }

    @PatchMapping("/me")
    public UserSummary updateCurrentUser(@CurrentUserId String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(userId, request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request);
        return withCookie(cookieFactory.create(result.rawRefreshToken())).body(result.authResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String rawRefreshToken) {
        RefreshResult result = authService.refresh(rawRefreshToken);
        return withCookie(cookieFactory.create(result.rawRefreshToken())).body(result.tokenResponse());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String rawRefreshToken) {
        authService.logout(rawRefreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString()).build();
    }

    private static ResponseEntity.BodyBuilder withCookie(ResponseCookie cookie) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
