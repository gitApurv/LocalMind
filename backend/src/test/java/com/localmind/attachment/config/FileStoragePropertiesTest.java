package com.localmind.attachment.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.localmind.attachment.model.SupportedFileType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Unit tests for {@link FileStorageProperties}: extension normalisation, the default of every supported file type and
 * startup rejection of an unsupported extension.
 */
class FileStoragePropertiesTest {

    @Test
    @DisplayName("Configured extensions are trimmed and lower-cased, so 'PDF' in the configuration still matches")
    void normalisesConfiguredExtensions() {
        FileStorageProperties properties = new FileStorageProperties("./uploads", DataSize.ofMegabytes(1),
                List.of(" PDF", "Md ", ""));

        assertEquals(List.of("pdf", "md"), properties.allowedExtensions());
        assertTrue(properties.isAllowedExtension("pdf"));
        assertTrue(properties.isAllowedExtension("MD"));
        assertFalse(properties.isAllowedExtension("txt"));
    }

    @Test
    @DisplayName("Without configuration every supported file type is allowed")
    void defaultsToEverySupportedType() {
        FileStorageProperties properties = new FileStorageProperties(null, null, null);

        assertEquals(List.of("pdf", "txt", "docx", "md"), properties.allowedExtensions());
        assertEquals(SupportedFileType.extensions(), properties.allowedExtensions());
    }

    @Test
    @DisplayName("An extension without a known content type is rejected at startup")
    void rejectsUnsupportedExtension() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FileStorageProperties(null, null, List.of("pdf", "EXE")));

        assertTrue(e.getMessage().contains("[exe]"));
    }
}
