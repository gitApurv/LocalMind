package com.localmind.attachment.dto;

/**
 * Response body of {@code POST /api/conversations/{conversationId}/attachments}: the new attachment's id, its initial
 * status ({@code PROCESSING}), file name and size.
 */
public record UploadResponse(String attachmentId, String status, String fileName, Long fileSize) {
}
