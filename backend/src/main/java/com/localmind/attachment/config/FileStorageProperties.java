package com.localmind.attachment.config;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "localmind.storage")
public record FileStorageProperties(String uploadDir, DataSize maxFileSize, List<String> allowedExtensions) {

    public FileStorageProperties {
        uploadDir = Objects.requireNonNullElse(uploadDir, "./uploads");
        maxFileSize = Objects.requireNonNullElse(maxFileSize, DataSize.ofMegabytes(25));
        // Compared against lower-cased upload extensions, so "PDF" in the configuration still matches.
        allowedExtensions = Objects.requireNonNullElse(allowedExtensions, List.of("pdf", "txt", "docx", "md")).stream()
                .map(extension -> extension.strip().toLowerCase(Locale.ROOT)).filter(extension -> !extension.isEmpty())
                .toList();
    }

    public boolean isAllowedExtension(String extension) {
        return extension != null && allowedExtensions.contains(extension.toLowerCase());
    }
}
