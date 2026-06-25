package com.edubase.commonCore.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class JwtSecretKeyProvider {

    private static final int HS256_MIN_KEY_BYTES = 32;
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private JwtSecretKeyProvider() {
    }

    public static SecretKey hmacSha256Key(String secret) {
        return new SecretKeySpec(resolveKeyBytes(secret), HMAC_SHA_256);
    }

    public static byte[] resolveKeyBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret is missing. Set JWT_SECRET environment variable.");
        }

        String trimmed = secret.trim();
        byte[] decoded = decodeBase64(trimmed);
        if (decoded != null && decoded.length >= HS256_MIN_KEY_BYTES) {
            return decoded;
        }

        byte[] rawBytes = trimmed.getBytes(StandardCharsets.UTF_8);
        if (rawBytes.length < HS256_MIN_KEY_BYTES) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes, or a Base64 value that decodes to 32+ bytes.");
        }

        return rawBytes;
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            try {
                return Base64.getUrlDecoder().decode(value);
            } catch (IllegalArgumentException ignoredUrlSafe) {
                return null;
            }
        }
    }
}
