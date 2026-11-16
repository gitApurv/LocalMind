package com.localmind.attachment.service;

import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Document processing runs in memory ({@code @Async}), so a restart abandons any attachment still being processed and
 * it would stay {@code PROCESSING} forever, with the frontend polling it. At startup no job is running yet, so every
 * such attachment is marked failed and the user can upload it again. LocalMind runs as a single instance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterruptedIngestionRecovery {

    static final String INTERRUPTED_MESSAGE = "Processing was interrupted by a server restart. Please upload the "
            + "file again.";

    private final AttachmentRepository attachmentRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedIngestions() {
        try {
            int failed = attachmentRepository.failAllWithStatus(Attachment.Status.PROCESSING, Attachment.Status.FAILED,
                    INTERRUPTED_MESSAGE);
            if (failed > 0) {
                log.warn("Marked {} attachments interrupted by a restart as failed", failed);
            }
        } catch (RuntimeException e) {
            log.warn("Could not mark interrupted attachments as failed", e);
        }
    }
}
