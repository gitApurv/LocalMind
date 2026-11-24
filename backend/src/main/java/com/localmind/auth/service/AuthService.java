package com.localmind.auth.service;

import com.localmind.auth.dto.AuthResponse;
import com.localmind.auth.dto.LoginRequest;
import com.localmind.auth.dto.RefreshTokenResponse;
import com.localmind.auth.dto.RegisterRequest;
import com.localmind.auth.dto.UpdateProfileRequest;
import com.localmind.auth.dto.UserSummary;
import com.localmind.auth.entity.RefreshToken;
import com.localmind.auth.model.LoginResult;
import com.localmind.auth.model.RefreshResult;
import com.localmind.auth.repository.RefreshTokenRepository;
import com.localmind.common.exception.BadRequestException;
import com.localmind.common.exception.ConflictException;
import com.localmind.common.exception.UnauthorizedException;
import com.localmind.common.security.JwtProperties;
import com.localmind.user.entity.User;
import com.localmind.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.function.SingletonSupplier;

/**
 * Registers users, logs them in, rotates and revokes refresh tokens, and reads or updates the caller's profile. Only
 * the SHA-256 hash of each refresh token is stored, and emails are trimmed and lowercased before every lookup.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String EMAIL_TAKEN = "Email is already registered";
    private static final String USER_NOT_FOUND = "User not found";
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(10);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SingletonSupplier<String> unknownUserHash = SingletonSupplier.of(this::newUnknownUserHash);

    @Transactional
    public UserSummary register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadRequestException("Password must not exceed " + MAX_PASSWORD_BYTES + " bytes");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(EMAIL_TAKEN);
        }
        String passwordHash = passwordEncoder.encode(request.password());
        String fullName = request.fullName().trim();
        User user = User.builder().email(email).passwordHash(passwordHash).fullName(fullName).build();
        try {
            User saved = userRepository.saveAndFlush(user);
            return UserSummary.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(EMAIL_TAKEN, e);
        }
    }

    @Transactional(readOnly = true)
    public UserSummary getCurrentUser(String userId) {
        User user = findUser(userId);
        return UserSummary.from(user);
    }

    @Transactional
    public UserSummary updateProfile(String userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.customInstructions() != null) {
            user.setCustomInstructions(request.customInstructions().trim());
        }
        User saved = userRepository.save(user);
        return UserSummary.from(saved);
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> candidate = userRepository.findByEmail(email);
        String hash = candidate.map(User::getPasswordHash).orElseGet(unknownUserHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        User user = candidate.filter(found -> passwordMatches)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));

        String rawRefreshToken = issueRefreshToken(user);
        String accessToken = jwtService.generateAccessToken(user);
        long ttlSeconds = accessTokenTtlSeconds();
        UserSummary summary = UserSummary.from(user);
        AuthResponse response = new AuthResponse(accessToken, TOKEN_TYPE, ttlSeconds, summary);
        return new LoginResult(response, rawRefreshToken);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Missing refresh token cookie");
        }
        String tokenHash = hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        Instant now = Instant.now();
        if (storedToken.isRevoked() && !storedToken.isRecentlyRotated(now, ROTATION_GRACE)) {
            refreshTokenRepository.deleteByUserId(storedToken.getUserId());
            throw new UnauthorizedException("Refresh token reuse detected: session terminated");
        }
        if (storedToken.isExpired()) {
            throw new UnauthorizedException("Refresh token expired");
        }
        if (!storedToken.isRevoked()) {
            storedToken.rotate(now);
            refreshTokenRepository.save(storedToken);
        }

        User user = findUser(storedToken.getUserId());
        String newRawRefreshToken = issueRefreshToken(user);
        String accessToken = jwtService.generateAccessToken(user);
        long ttlSeconds = accessTokenTtlSeconds();
        RefreshTokenResponse response = new RefreshTokenResponse(accessToken, TOKEN_TYPE, ttlSeconds);
        return new RefreshResult(response, newRawRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(refreshTokenRepository::delete);
    }

    private String newUnknownUserHash() {
        return passwordEncoder.encode(UUID.randomUUID().toString());
    }

    private User findUser(String userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UnauthorizedException(USER_NOT_FOUND));
    }

    private String issueRefreshToken(User user) {
        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(jwtProperties.refreshTokenExpiration());
        RefreshToken token = RefreshToken.builder().userId(user.getId()).tokenHash(tokenHash).expiresAt(expiresAt)
                .revoked(false).build();
        refreshTokenRepository.save(token);
        return rawToken;
    }

    private long accessTokenTtlSeconds() {
        return jwtProperties.accessTokenExpiration().toSeconds();
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String hashToken(String rawToken) {
        try {
            byte[] tokenBytes = rawToken.getBytes(StandardCharsets.UTF_8);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(tokenBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
