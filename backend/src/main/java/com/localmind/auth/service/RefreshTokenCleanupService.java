package com.localmind.auth.service;

import com.localmind.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Deletes expired refresh tokens, which would otherwise accumulate forever: every login and every refresh inserts a
 * row. Only expired rows go. Rotated tokens that have not expired yet are kept, because replaying one is how
 * {@link AuthService#refresh} detects a stolen token; once expired, a token is rejected anyway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private static final long HOUR_MILLIS = 60 * 60 * 1000L;
    private static final long STARTUP_DELAY_MILLIS = 60 * 1000L;

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(initialDelay = STARTUP_DELAY_MILLIS, fixedDelay = HOUR_MILLIS)
    public void scheduledCleanup() {
        try {
            deleteExpiredTokens();
        } catch (RuntimeException e) {
            log.warn("Refresh token cleanup failed; retrying in an hour", e);
        }
    }

    /** Returns the number of deleted tokens. The delete runs in its own transaction (see the repository). */
    public int deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
        return deleted;
    }
}
