package com.localmind.attachment.storage;

import com.localmind.attachment.config.FileStorageProperties;
import com.localmind.attachment.entity.Attachment;
import com.localmind.common.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

/**
 * Stores uploads on local disk under {@code <uploadDir>/<userId>/<attachmentId>_<sanitizedName>}. File names are
 * reduced to safe characters and every path is checked to stay inside the user's directory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalDiskStorageService implements FileStorageService {

    private static final String UNSAFE_FILENAME_CHARS = "[^a-zA-Z0-9._-]";
    private static final String FALLBACK_FILENAME = "attachment";

    private final FileStorageProperties properties;

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
            URI uri = targetPath.toUri();
            Resource resource = new UrlResource(uri);
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            log.warn("Stored file of attachment '{}' is missing or unreadable", attachmentId);
            throw new ResourceNotFoundException("File not found");
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

    private Path resolveSafePath(String userId, String attachmentId, String originalFileName) {
        if (userId == null || userId.isBlank() || attachmentId == null || attachmentId.isBlank()) {
            throw new IllegalArgumentException("User ID and Attachment ID must not be blank");
        }
        Path rootLocation = rootLocation();
        Path userDir = rootLocation.resolve(userId).normalize();
        Path targetPath = userDir.resolve(attachmentId + "_" + sanitizeFileName(originalFileName)).normalize();
        if (!userDir.startsWith(rootLocation) || !targetPath.startsWith(userDir)) {
            throw new SecurityException("Directory traversal attempt detected in path: " + originalFileName);
        }
        return targetPath;
    }

    private Path rootLocation() {
        return Paths.get(properties.uploadDir()).toAbsolutePath().normalize();
    }

    private static String sanitizeFileName(String originalFileName) {
        String rawName = originalFileName != null ? originalFileName : Attachment.UNNAMED_FILE_NAME;
        String baseName = baseName(rawName);
        String safeName = baseName.replaceAll(UNSAFE_FILENAME_CHARS, "_");
        return safeName.isBlank() ? FALLBACK_FILENAME : safeName;
    }

    private static String baseName(String rawName) {
        try {
            Path fileName = Paths.get(rawName).getFileName();
            return fileName != null ? fileName.toString() : rawName;
        } catch (InvalidPathException e) {
            return rawName;
        }
    }
}
