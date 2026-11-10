package com.localmind.common.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
