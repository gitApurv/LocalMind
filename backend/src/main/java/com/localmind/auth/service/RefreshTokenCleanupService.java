package com.localmind.auth.service;

import com.localmind.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes expired refresh tokens, which would otherwise accumulate forever: every login and every refresh inserts a
 * row. Only expired rows go. Rotated tokens that have not expired yet are kept, because replaying one is how
 * {@link AuthService#refresh} detects a stolen token; once expired, a token is rejected anyway. Scheduled by
 * {@link RefreshTokenCleanupScheduler}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public int deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
        return deleted;
    }
}
