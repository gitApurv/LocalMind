package com.localmind.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RefreshTokenCleanupScheduler}: delegation to the cleanup service and swallowing failures so the
 * next run still happens.
 */
class RefreshTokenCleanupSchedulerTest {

    private final RefreshTokenCleanupService cleanupService = mock(RefreshTokenCleanupService.class);
    private final RefreshTokenCleanupScheduler scheduler = new RefreshTokenCleanupScheduler(cleanupService);

    @Test
    @DisplayName("scheduledCleanup() delegates to the transactional cleanup service")
    void scheduledCleanupDelegates() {
        scheduler.scheduledCleanup();

        verify(cleanupService).deleteExpiredTokens();
    }

    @Test
    @DisplayName("scheduledCleanup() logs a failure instead of throwing, so the next run still happens")
    void scheduledCleanupSwallowsFailures() {
        when(cleanupService.deleteExpiredTokens()).thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(scheduler::scheduledCleanup);
    }
}
