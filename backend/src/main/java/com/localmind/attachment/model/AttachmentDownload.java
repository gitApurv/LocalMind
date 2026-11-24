package com.localmind.attachment.model;

import org.springframework.core.io.Resource;

/** Everything the download endpoint needs to stream a stored attachment. */
public record AttachmentDownload(Resource resource, String fileName, String contentType) {
}
