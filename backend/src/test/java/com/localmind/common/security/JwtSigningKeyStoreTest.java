package com.localmind.common.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link JwtSigningKeyStore}: the key survives a restart and the key file is readable and writable by
 * its owner only.
 */
class JwtSigningKeyStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("loadOrCreate() should persist the key and return the same key on the next start")
    void keySurvivesRestart() {
        Path keyFile = tempDir.resolve("keys/jwt.pem");

        KeyPair first = JwtSigningKeyStore.loadOrCreate(keyFile);
        KeyPair second = JwtSigningKeyStore.loadOrCreate(keyFile);

        assertTrue(Files.exists(keyFile));
        assertArrayEquals(first.getPrivate().getEncoded(), second.getPrivate().getEncoded());
        assertArrayEquals(first.getPublic().getEncoded(), second.getPublic().getEncoded());
    }

    @Test
    @DisplayName("loadOrCreate() should create the key file readable and writable by its owner only")
    void keyFileIsOwnerOnly() throws IOException {
        assumeTrue(tempDir.getFileSystem().supportedFileAttributeViews().contains("posix"),
                "File system without POSIX permissions");
        Path keyFile = tempDir.resolve("jwt.pem");

        JwtSigningKeyStore.loadOrCreate(keyFile);

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(keyFile)));
    }
}
