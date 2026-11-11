package com.localmind.attachment.controller;

import com.localmind.attachment.dto.AttachmentResponse;
import com.localmind.attachment.dto.UploadResponse;
import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.service.AttachmentService;
import com.localmind.common.security.CurrentUserId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/conversations/{conversationId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public UploadResponse uploadAttachment(@CurrentUserId String userId,
            @PathVariable("conversationId") String conversationId, @RequestParam("file") MultipartFile file) {
        return attachmentService.uploadAttachment(conversationId, userId, file);
    }

    @GetMapping
    public List<AttachmentResponse> listAttachments(@CurrentUserId String userId,
            @PathVariable("conversationId") String conversationId) {
        return attachmentService.listAttachments(conversationId, userId);
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(@CurrentUserId String userId,
            @PathVariable("conversationId") String conversationId, @PathVariable("attachmentId") String attachmentId) {
        Attachment attachment = attachmentService.getAttachment(conversationId, attachmentId, userId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachment.getFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(mediaTypeOf(attachment))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(attachmentService.loadAttachmentFile(attachment));
    }

    private static MediaType mediaTypeOf(Attachment attachment) {
        if (attachment.getFileType() == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(attachment.getFileType());
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
