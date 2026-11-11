package com.localmind.attachment.storage;

import com.localmind.attachment.config.FileStorageProperties;
import com.localmind.common.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

/**
 * Stores uploads under {@code <uploadDir>/<userId>/<attachmentId>_<sanitizedName>}.
 */
@Slf4j
@Service
public class LocalDiskStorageService implements FileStorageService {

    private static final String UNSAFE_FILENAME_CHARS = "[^a-zA-Z0-9._-]";
    private static final String FALLBACK_FILENAME = "attachment";

    private final Path rootLocation;

    public LocalDiskStorageService(FileStorageProperties properties) {
        this.rootLocation = Paths.get(properties.uploadDir()).toAbsolutePath().normalize();
    }

    @Override
    public void store(InputStream inputStream, String userId, String attachmentId, String originalFileName) {
        Path targetPath = resolveSafePath(userId, attachmentId, originalFileName);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file: " + originalFileName, e);
        }
    }

    @Override
    public Resource loadAsResource(String userId, String attachmentId, String originalFileName) {
        Path targetPath = resolveSafePath(userId, attachmentId, originalFileName);
        try {
            Resource resource = new UrlResource(targetPath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new ResourceNotFoundException("File not found or unreadable: " + originalFileName);
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Invalid file path URI", e);
        }
    }

    @Override
    public void delete(String userId, String attachmentId, String originalFileName) {
        Path targetPath = resolveSafePath(userId, attachmentId, originalFileName);
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            log.warn("Failed to delete file at {}: {}", targetPath, e.getMessage());
        }
    }

    /**
     * Builds the storage path and rejects anything that escapes the user's directory (directory traversal guard).
     */
    private Path resolveSafePath(String userId, String attachmentId, String originalFileName) {
        if (userId == null || userId.isBlank() || attachmentId == null || attachmentId.isBlank()) {
            throw new IllegalArgumentException("User ID and Attachment ID must not be blank");
        }
        Path userDir = rootLocation.resolve(userId).normalize();
        Path targetPath = userDir.resolve(attachmentId + "_" + sanitizeFileName(originalFileName)).normalize();
        if (!userDir.startsWith(rootLocation) || !targetPath.startsWith(userDir)) {
            throw new SecurityException("Directory traversal attempt detected in path: " + originalFileName);
        }
        return targetPath;
    }

    private static String sanitizeFileName(String originalFileName) {
        String baseName = Paths.get(originalFileName != null ? originalFileName : "unnamed").getFileName().toString();
        String safeName = baseName.replaceAll(UNSAFE_FILENAME_CHARS, "_");
        return safeName.isBlank() ? FALLBACK_FILENAME : safeName;
    }
}
