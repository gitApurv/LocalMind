package com.localmind.attachment.service;

import com.localmind.attachment.config.FileStorageProperties;
import com.localmind.attachment.dto.AttachmentResponse;
import com.localmind.attachment.dto.UploadResponse;
import com.localmind.attachment.entity.Attachment;
import com.localmind.attachment.model.AttachmentDownload;
import com.localmind.attachment.model.SupportedFileType;
import com.localmind.attachment.repository.AttachmentRepository;
import com.localmind.attachment.storage.FileStorageService;
import com.localmind.common.exception.BadRequestException;
import com.localmind.common.transaction.TransactionHooks;
import com.localmind.conversation.service.OwnershipGuard;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Uploads, lists, serves and deletes a conversation's attachments, checking ownership with {@link OwnershipGuard}. An
 * upload is validated, stored and saved as {@code PROCESSING}, then handed to {@link DocumentEtlService} after commit;
 * a rolled-back upload deletes its stored file. Content types are derived from the file extension, never taken from the
 * client.
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    static final int MAX_FILE_NAME_LENGTH = 255;
    static final String ACCESS_DENIED_MESSAGE = "Access denied: Attachment does not belong to this conversation";
    private static final String OCTET_STREAM = "application/octet-stream";

    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentEtlService documentEtlService;
    private final OwnershipGuard ownershipGuard;
    private final FileStorageProperties fileStorageProperties;

    @Transactional
    public UploadResponse uploadAttachment(String conversationId, String userId, MultipartFile file) {
        ownershipGuard.lockOwnedConversation(conversationId, userId);
        validateUpload(file);

        String attachmentId = UUID.randomUUID().toString();
        String fileName = originalFileName(file);
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
    public AttachmentDownload getAttachmentDownload(String conversationId, String attachmentId, String userId) {
        ownershipGuard.validateConversationOwnership(conversationId, userId);
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(found -> found.getUserId().equals(userId) && found.getConversationId().equals(conversationId))
                .orElseThrow(() -> new AccessDeniedException(ACCESS_DENIED_MESSAGE));
        return new AttachmentDownload(
                fileStorageService.loadAsResource(attachment.getUserId(), attachment.getId(), attachment.getFileName()),
                attachment.getFileName(), contentTypeOf(attachment.getFileName()));
    }

    @Transactional
    public void deleteConversationAttachments(String conversationId) {
        List<Attachment> attachments = attachmentRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
        if (attachments.isEmpty()) {
            return;
        }
        attachmentRepository.deleteByConversationId(conversationId);
        TransactionHooks.afterCommit(() -> deleteStoredData(conversationId, attachments));
    }

    private static String contentTypeOf(String fileName) {
        return SupportedFileType.fromExtension(fileExtension(fileName)).map(SupportedFileType::contentType)
                .orElse(OCTET_STREAM);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file must not be empty");
        }
        if (file.getSize() > fileStorageProperties.maxFileSize().toBytes()) {
            throw new BadRequestException(
                    "File exceeds maximum size limit of " + fileStorageProperties.maxFileSize().toMegabytes() + "MB");
        }
        String originalName = originalFileName(file);
        if (originalName.length() > MAX_FILE_NAME_LENGTH) {
            throw new BadRequestException("File name must not exceed " + MAX_FILE_NAME_LENGTH + " characters");
        }
        String extension = fileExtension(originalName);
        if (!fileStorageProperties.isAllowedExtension(extension)) {
            throw new BadRequestException("Unsupported file format '" + extension + "'. Allowed: "
                    + fileStorageProperties.allowedExtensions());
        }
    }

    private static String originalFileName(MultipartFile file) {
        return Objects.requireNonNullElse(file.getOriginalFilename(), Attachment.UNNAMED_FILE_NAME);
    }

    private void storeFile(MultipartFile file, String userId, String attachmentId, String fileName) {
        try (InputStream inputStream = file.getInputStream()) {
            fileStorageService.store(inputStream, userId, attachmentId, fileName);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload stream", e);
        }
    }

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
