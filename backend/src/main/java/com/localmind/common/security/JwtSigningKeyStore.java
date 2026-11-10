package com.localmind.common.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the RSA key that signs access tokens from a PKCS#8 PEM file, generating and saving it on first start. Keeping
 * the key on disk means access tokens stay valid across restarts.
 */
@Slf4j
final class JwtSigningKeyStore {

    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";
    private static final int KEY_SIZE = 2048;

    private JwtSigningKeyStore() {
    }

    static KeyPair loadOrCreate(Path keyFile) {
        try {
            if (Files.exists(keyFile)) {
                return read(keyFile);
            }
            KeyPair keyPair = generate();
            write(keyFile, keyPair);
            log.info("Generated new JWT signing key at {}", keyFile.toAbsolutePath());
            return keyPair;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read or write JWT signing key at " + keyFile, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid JWT signing key at " + keyFile, e);
        }
    }

    private static KeyPair generate() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_SIZE);
        return generator.generateKeyPair();
    }

    private static KeyPair read(Path keyFile) throws IOException, GeneralSecurityException {
        String pem = Files.readString(keyFile, StandardCharsets.US_ASCII).replace(PEM_HEADER, "")
                .replace(PEM_FOOTER, "").replaceAll("\\s", "");
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) keyFactory
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        return new KeyPair(
                keyFactory
                        .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent())),
                privateKey);
    }

    private static void write(Path keyFile, KeyPair keyPair) throws IOException {
        Path parent = keyFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String pem = PEM_HEADER + "\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n" + PEM_FOOTER + "\n";
        Files.writeString(keyFile, pem, StandardCharsets.US_ASCII);
        try {
            Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            // Not a POSIX file system (Windows): rely on the directory's ACLs.
        }
    }
}
