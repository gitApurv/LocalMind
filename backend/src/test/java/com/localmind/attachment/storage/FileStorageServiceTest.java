package com.localmind.attachment.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.localmind.attachment.config.FileStorageProperties;
import com.localmind.common.exception.ResourceNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = FileStorageProperties.withUploadDir(tempDir.toString());
        fileStorageService = new LocalDiskStorageService(properties);
    }

    @Test
    @DisplayName("store() and loadAsResource() should persist and retrieve file bytes")
    void storeAndLoadSuccess() throws Exception {
        String content = "Hello LocalMind File Storage";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        String userId = "user-123";
        String attachmentId = "att-456";
        String fileName = "sample.txt";

        fileStorageService.store(inputStream, userId, attachmentId, fileName);
        Resource resource = fileStorageService.loadAsResource(userId, attachmentId, fileName);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());

        try (InputStream stream = resource.getInputStream()) {
            String loadedContent = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(content, loadedContent);
        }
    }

    @Test
    @DisplayName("delete() should remove file from disk")
    void deleteSuccess() {
        InputStream inputStream = new ByteArrayInputStream("To be deleted".getBytes(StandardCharsets.UTF_8));
        String userId = "user-123";
        String attachmentId = "att-delete";
        String fileName = "delete_me.txt";

        fileStorageService.store(inputStream, userId, attachmentId, fileName);
        assertTrue(fileStorageService.loadAsResource(userId, attachmentId, fileName).exists());

        fileStorageService.delete(userId, attachmentId, fileName);
        assertThrows(ResourceNotFoundException.class,
                () -> fileStorageService.loadAsResource(userId, attachmentId, fileName));
    }

    @Test
    @DisplayName("Path traversal attempts should be rejected with SecurityException")
    void pathTraversalRejected() {
        InputStream inputStream = new ByteArrayInputStream("Malicious content".getBytes(StandardCharsets.UTF_8));
        String userId = "user-123";
        String attachmentId = "att-evil";
        String maliciousFileName = "../../secret.txt";

        // Must either throw SecurityException or sanitize into a safe name within the
        // user directory
        assertDoesNotThrow(() -> fileStorageService.store(inputStream, userId, attachmentId, maliciousFileName));

        // The name was reduced to its last segment inside the user's own directory.
        assertTrue(Files.exists(tempDir.resolve("user-123").resolve("att-evil_secret.txt")));
        assertFalse(Files.exists(tempDir.resolve("secret.txt")));
    }
}
