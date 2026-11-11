package com.localmind.attachment.config;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "localmind.storage")
public record FileStorageProperties(String uploadDir, DataSize maxFileSize, List<String> allowedExtensions) {

    public FileStorageProperties {
        uploadDir = Objects.requireNonNullElse(uploadDir, "./uploads");
        maxFileSize = Objects.requireNonNullElse(maxFileSize, DataSize.ofMegabytes(25));
        allowedExtensions = List
                .copyOf(Objects.requireNonNullElse(allowedExtensions, List.of("pdf", "txt", "docx", "md")));
    }

    public static FileStorageProperties withUploadDir(String uploadDir) {
        return new FileStorageProperties(uploadDir, null, null);
    }

    public boolean isAllowedExtension(String extension) {
        return extension != null && allowedExtensions.contains(extension.toLowerCase());
    }
}
