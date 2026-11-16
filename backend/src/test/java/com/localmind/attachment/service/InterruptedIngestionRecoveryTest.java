package com.localmind.attachment.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterruptedIngestionRecoveryTest {

    private final AttachmentRepository repository = mock(AttachmentRepository.class);
    private final InterruptedIngestionRecovery recovery = new InterruptedIngestionRecovery(repository);

    @Test
    @DisplayName("failInterruptedIngestions() marks attachments left PROCESSING by a restart as FAILED")
    void failsAttachmentsStillProcessing() {
        recovery.failInterruptedIngestions();

        verify(repository).failAllWithStatus(Attachment.Status.PROCESSING, Attachment.Status.FAILED,
                InterruptedIngestionRecovery.INTERRUPTED_MESSAGE);
    }

    @Test
    @DisplayName("failInterruptedIngestions() never blocks startup when the database is unavailable")
    void startupSurvivesDatabaseFailure() {
        when(repository.failAllWithStatus(any(), any(), any())).thenThrow(new IllegalStateException("down"));

        assertDoesNotThrow(recovery::failInterruptedIngestions);
    }
}
