package com.localmind.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link RefreshTokenCleanupService#deleteExpiredTokens()} every hour, starting one minute after startup. It is a
 * separate bean so the call goes through the service's transactional proxy; a self-invocation inside the service would
 * run the delete without a transaction. A failed run is logged and retried an hour later.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private static final long HOUR_MILLIS = 60 * 60 * 1000L;
    private static final long STARTUP_DELAY_MILLIS = 60 * 1000L;

    private final RefreshTokenCleanupService cleanupService;

    @Scheduled(initialDelay = STARTUP_DELAY_MILLIS, fixedDelay = HOUR_MILLIS)
    public void scheduledCleanup() {
        try {
            cleanupService.deleteExpiredTokens();
        } catch (RuntimeException e) {
            log.warn("Refresh token cleanup failed; retrying in an hour", e);
        }
    }
}
