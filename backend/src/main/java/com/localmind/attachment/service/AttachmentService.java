package com.localmind.attachment.service;

import com.localmind.attachment.config.FileStorageProperties;
import com.localmind.attachment.dto.AttachmentResponse;
import com.localmind.attachment.dto.UploadResponse;
import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.attachment.storage.FileStorageService;
import com.localmind.common.exception.BadRequestException;
import com.localmind.common.exception.ResourceNotFoundException;
import com.localmind.common.transaction.TransactionHooks;
import com.localmind.conversation.service.OwnershipGuard;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final String UNNAMED_FILE = "unnamed";
    /** Length of {@code attachments.file_name}. */
    static final int MAX_FILE_NAME_LENGTH = 255;
    private static final String OCTET_STREAM = "application/octet-stream";
    /** Content types are derived from the extension, never taken from the client. */
    private static final Map<String, String> CONTENT_TYPES = Map.of("pdf", "application/pdf", "txt", "text/plain",
            "md", "text/markdown", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentEtlService documentEtlService;
    private final OwnershipGuard ownershipGuard;
    private final FileStorageProperties fileStorageProperties;

    @Transactional
    public UploadResponse uploadAttachment(String conversationId, String userId, MultipartFile file) {
        ownershipGuard.validateConversationOwnership(conversationId, userId);
        validateUpload(file);

        String attachmentId = UUID.randomUUID().toString();
        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), UNNAMED_FILE);
        // Registered before storing so a copy that fails half-way is removed with the rolled-back row.
        TransactionHooks.onOutcome(() -> documentEtlService.processAttachment(attachmentId),
                () -> fileStorageService.delete(userId, attachmentId, fileName));
        storeFile(file, userId, attachmentId, fileName);

        Attachment attachment = Attachment.builder().id(attachmentId).conversationId(conversationId).userId(userId)
                .fileName(fileName).fileType(contentTypeOf(fileName)).fileSize(file.getSize())
                .status(Attachment.Status.PROCESSING).build();
        attachmentRepository.save(attachment);

        return new UploadResponse(attachmentId, Attachment.Status.PROCESSING.name(), fileName, file.getSize());
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listAttachments(String conversationId, String userId) {
        ownershipGuard.validateConversationOwnership(conversationId, userId);
        return attachmentRepository.findByConversationIdOrderByCreatedAtDesc(conversationId).stream()
                .map(AttachmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Attachment getAttachment(String conversationId, String attachmentId, String userId) {
        ownershipGuard.validateConversationOwnership(conversationId, userId);
        return attachmentRepository.findByIdAndUserId(attachmentId, userId)
                .filter(attachment -> attachment.getConversationId().equals(conversationId))
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
    }

    /**
     * Deletes every attachment of a conversation. Stored files and indexed chunks are removed only after the deletion
     * commits, so a rollback never leaves rows pointing at missing files.
     */
    @Transactional
    public void deleteConversationAttachments(String conversationId) {
        List<Attachment> attachments = attachmentRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
        if (attachments.isEmpty()) {
            return;
        }
        attachmentRepository.deleteByConversationId(conversationId);
        TransactionHooks.afterCommit(() -> deleteStoredData(conversationId, attachments));
    }

    /**
     * The type a download is served with, from the file extension. Rows stored before types were derived on the server
     * may hold a client-supplied type, so the stored value is not used.
     */
    public String contentTypeOf(Attachment attachment) {
        return contentTypeOf(attachment.getFileName());
    }

    private static String contentTypeOf(String fileName) {
        return CONTENT_TYPES.getOrDefault(fileExtension(fileName).toLowerCase(Locale.ROOT), OCTET_STREAM);
    }

    public Resource loadAttachmentFile(Attachment attachment) {
        return fileStorageService.loadAsResource(attachment.getUserId(), attachment.getId(), attachment.getFileName());
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file must not be empty");
        }
        if (file.getSize() > fileStorageProperties.maxFileSize().toBytes()) {
            throw new BadRequestException(
                    "File exceeds maximum size limit of " + fileStorageProperties.maxFileSize().toMegabytes() + "MB");
        }
        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), UNNAMED_FILE);
        if (originalName.length() > MAX_FILE_NAME_LENGTH) {
            throw new BadRequestException("File name must not exceed " + MAX_FILE_NAME_LENGTH + " characters");
        }
        String extension = fileExtension(originalName);
        if (!fileStorageProperties.isAllowedExtension(extension)) {
            throw new BadRequestException("Unsupported file format '" + extension + "'. Allowed: "
                    + fileStorageProperties.allowedExtensions());
        }
    }

    private void storeFile(MultipartFile file, String userId, String attachmentId, String fileName) {
        try (InputStream inputStream = file.getInputStream()) {
            fileStorageService.store(inputStream, userId, attachmentId, fileName);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload stream", e);
        }
    }

    /** Returns the extension after the last dot, ignoring leading-dot names. */
    private static String fileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1) : "";
    }

    private void deleteStoredData(String conversationId, List<Attachment> attachments) {
        attachments.forEach(attachment -> fileStorageService.delete(attachment.getUserId(), attachment.getId(),
                attachment.getFileName()));
        attachments.stream().map(Attachment::getUserId).distinct()
                .forEach(userId -> documentEtlService.deleteIndexedChunks(userId, conversationId));
    }
}
