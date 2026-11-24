package com.localmind.common.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Set;
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
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

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
        byte[] privateKeyBytes = Base64.getDecoder().decode(pem);
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) keyFactory
                .generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
        return new KeyPair(publicKey, privateKey);
    }

    private static void write(Path keyFile, KeyPair keyPair) throws IOException {
        Path parent = keyFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] lineSeparator = "\n".getBytes(StandardCharsets.US_ASCII);
        byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
        String body = Base64.getMimeEncoder(64, lineSeparator).encodeToString(privateKeyBytes);
        String pem = PEM_HEADER + "\n" + body + "\n" + PEM_FOOTER + "\n";
        writeOwnerOnly(keyFile, pem.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeOwnerOnly(Path keyFile, byte[] content) throws IOException {
        Set<OpenOption> options = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        boolean posix = keyFile.getFileSystem().supportedFileAttributeViews().contains("posix");
        FileAttribute<?>[] attributes = posix
                ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(OWNER_ONLY)}
                : new FileAttribute<?>[0];
        try (SeekableByteChannel channel = Files.newByteChannel(keyFile, options, attributes)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }
}
