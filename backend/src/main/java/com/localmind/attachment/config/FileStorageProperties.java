package com.localmind.attachment.config;

import com.localmind.attachment.model.SupportedFileType;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Settings bound from {@code localmind.storage}: upload directory, maximum file size and allowed file extensions.
 * Defaults are {@code ./uploads}, 25 MB and every {@link SupportedFileType}. Extensions are lower-cased, and one that
 * is not a {@link SupportedFileType} fails startup.
 */
@ConfigurationProperties(prefix = "localmind.storage")
public record FileStorageProperties(String uploadDir, DataSize maxFileSize, List<String> allowedExtensions) {

    public FileStorageProperties {
        uploadDir = Objects.requireNonNullElse(uploadDir, "./uploads");
        maxFileSize = Objects.requireNonNullElse(maxFileSize, DataSize.ofMegabytes(25));
        allowedExtensions = Objects.requireNonNullElse(allowedExtensions, SupportedFileType.extensions()).stream()
                .map(extension -> extension.strip().toLowerCase(Locale.ROOT)).filter(extension -> !extension.isEmpty())
                .toList();
        rejectUnsupported(allowedExtensions);
    }

    public boolean isAllowedExtension(String extension) {
        return extension != null && allowedExtensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    private static void rejectUnsupported(List<String> extensions) {
        List<String> unsupported = extensions.stream()
                .filter(extension -> SupportedFileType.fromExtension(extension).isEmpty()).toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException("localmind.storage.allowed-extensions contains unsupported extensions "
                    + unsupported + "; supported: " + SupportedFileType.extensions());
        }
    }
}
