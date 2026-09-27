package com.logicscope.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class JavaAgentResolver {
    public Path resolve(TargetRuntimeConfig.Otel otel) throws IOException {
        Path agent = otel.javaAgentPath().toAbsolutePath().normalize();
        if (!Files.isRegularFile(agent) || !Files.isReadable(agent)) {
            throw new IOException("OpenTelemetry Java Agent is unavailable at the configured local artifact");
        }
        String digest = sha256(agent);
        if (!MessageDigest.isEqual(digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                otel.javaAgentSha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IOException("OpenTelemetry Java Agent checksum does not match the configured SHA-256 digest");
        }
        return agent;
    }

    static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
