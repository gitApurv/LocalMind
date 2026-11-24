package com.localmind.attachment.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The document types LocalMind can ingest, with the content type each is served with. The single source of truth for
 * both the default {@code localmind.storage.allowed-extensions} and the download {@code Content-Type}, so an allowed
 * extension always has a known type.
 */
public enum SupportedFileType {

    PDF("pdf", "application/pdf"), TXT("txt", "text/plain"), DOCX("docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"), MD("md", "text/markdown");

    private final String extension;
    private final String contentType;

    SupportedFileType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<SupportedFileType> fromExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        String normalized = extension.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.extension.equals(normalized)).findFirst();
    }

    public static List<String> extensions() {
        return Arrays.stream(values()).map(SupportedFileType::extension).toList();
    }
}
