package com.localmind.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localmind.auth.repository.RefreshTokenRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenCleanupServiceTest {

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenCleanupService service = new RefreshTokenCleanupService(repository);

    @Test
    @DisplayName("deleteExpiredTokens() deletes tokens that expired before now and returns the count")
    void deletesTokensExpiredBeforeNow() {
        when(repository.deleteExpiredBefore(any())).thenReturn(3);
        Instant before = Instant.now();

        assertEquals(3, service.deleteExpiredTokens());

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteExpiredBefore(cutoff.capture());
        assertTrue(
                !cutoff.getValue().isBefore(before) && cutoff.getValue().isBefore(before.plus(Duration.ofMinutes(1))));
    }

    @Test
    @DisplayName("scheduledCleanup() logs a failure instead of throwing, so the next run still happens")
    void scheduledCleanupSwallowsFailures() {
        when(repository.deleteExpiredBefore(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(service::scheduledCleanup);
    }
}
