package com.localmind.attachment.dto;

import com.localmind.attachment.entity.Attachment;
import java.time.Instant;

public record AttachmentResponse(String id, String conversationId, String userId, String fileName, String fileType,
        Long fileSize, String status, Integer chunkCount, String errorMessage, Instant createdAt) {

    public static AttachmentResponse from(Attachment attachment) {
        String status = attachment.getStatus() != null ? attachment.getStatus().name() : null;
        return new AttachmentResponse(attachment.getId(), attachment.getConversationId(), attachment.getUserId(),
                attachment.getFileName(), attachment.getFileType(), attachment.getFileSize(), status,
                attachment.getChunkCount(), attachment.getErrorMessage(), attachment.getCreatedAt());
    }
}
