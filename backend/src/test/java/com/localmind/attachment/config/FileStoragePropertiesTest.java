package com.localmind.attachment.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class FileStoragePropertiesTest {

    @Test
    @DisplayName("Configured extensions are trimmed and lower-cased, so 'PDF' in the configuration still matches")
    void normalisesConfiguredExtensions() {
        FileStorageProperties properties = new FileStorageProperties("./uploads", DataSize.ofMegabytes(1),
                List.of(" PDF", "Md ", ""));

        assertEquals(List.of("pdf", "md"), properties.allowedExtensions());
        assertTrue(properties.isAllowedExtension("pdf"));
        assertTrue(properties.isAllowedExtension("MD"));
    }
}
