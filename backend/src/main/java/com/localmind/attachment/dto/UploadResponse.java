package com.localmind.attachment.dto;

public record UploadResponse(String attachmentId, String status, String fileName, Long fileSize) {
}
