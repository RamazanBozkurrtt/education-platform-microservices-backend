package com.edubase.auth.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Getter
@Slf4j
@ConditionalOnProperty(prefix = "app.gmail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GmailCredentialsProvider {

    private final ObjectMapper objectMapper;
    private final GmailProperties gmailProperties;

    private String username;
    private String password;
    private Path resolvedKeysPath;

    @PostConstruct
    void load() {
        String configuredUsername = trimToNull(gmailProperties.getUsername());
        String configuredPassword = trimToNull(gmailProperties.getPassword());

        if (configuredUsername != null && configuredPassword != null) {
            this.username = configuredUsername;
            this.password = sanitizeAndValidateAppPassword(
                    configuredPassword,
                    "app.gmail.password (GMAIL_APP_PASSWORD)"
            );
            this.resolvedKeysPath = null;
            log.info("Gmail credentials loaded from app.gmail.username/app.gmail.password.");
            return;
        }

        Path keysPath = resolveKeysPath();
        this.resolvedKeysPath = keysPath;
        JsonNode root = readJson(keysPath);
        JsonNode gmailNode = root.path("gmail");

        if (!gmailNode.isObject()) {
            throw new IllegalStateException("keys.json must include an object field named 'gmail'.");
        }

        String resolvedUsername = firstNonBlank(gmailNode, "username", "mail", "email");
        String resolvedPassword = firstNonBlank(gmailNode, "app-password", "appPassword", "app_password");

        if (resolvedUsername == null) {
            throw new IllegalStateException("Gmail username/mail is missing under keys.json -> gmail.");
        }
        if (resolvedPassword == null) {
            String accountPassword = extractStringValue(gmailNode.get("account-password"));
            String plainPassword = extractStringValue(gmailNode.get("password"));
            if ((accountPassword != null && !accountPassword.isBlank()) || (plainPassword != null && !plainPassword.isBlank())) {
                throw new IllegalStateException(
                        "keys.json -> gmail contains account password field, but Gmail SMTP requires app password. " +
                        "Use only app-password (or appPassword/app_password)."
                );
            }
            throw new IllegalStateException("Gmail app password is missing under keys.json -> gmail.");
        }

        this.username = resolvedUsername;
        this.password = sanitizeAndValidateAppPassword(resolvedPassword, "keys.json -> gmail.app-password");
        log.info("Gmail credentials loaded from {}", keysPath);
    }

    private Path resolveKeysPath() {
        String configuredPath = trimToNull(gmailProperties.getKeysFile());
        Set<Path> candidates = new LinkedHashSet<>();

        if (configuredPath != null) {
            Path configured = Path.of(configuredPath.trim());
            if (configured.isAbsolute()) {
                candidates.add(configured.normalize());
            } else {
                Path cwd = Path.of("").toAbsolutePath().normalize();
                candidates.add(cwd.resolve(configured).normalize());
            }
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        candidates.add(cwd.resolve("keys.json"));

        if (cwd.getParent() != null) {
            candidates.add(cwd.getParent().resolve("keys.json"));
        }

        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Gmail credentials could not be resolved. " +
                "Provide app.gmail.username/app.gmail.password (or env GMAIL_USERNAME/GMAIL_APP_PASSWORD), " +
                "or provide a valid keys file path via app.gmail.keys-file. Checked: " + new ArrayList<>(candidates)
        );
    }

    private JsonNode readJson(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return objectMapper.readTree(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read keys.json at " + path, e);
        }
    }

    private String firstNonBlank(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode valueNode = node.get(fieldName);
            String value = extractStringValue(valueNode);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String extractStringValue(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }

        if (valueNode.isTextual()) {
            return valueNode.asText().trim();
        }

        if (valueNode.isObject()) {
            String fromKeyField = extractStringValue(valueNode.get("key"));
            if (fromKeyField != null && !fromKeyField.isBlank()) {
                return fromKeyField;
            }

            String fromValueField = extractStringValue(valueNode.get("value"));
            if (fromValueField != null && !fromValueField.isBlank()) {
                return fromValueField;
            }
        }

        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String sanitizeAndValidateAppPassword(String rawPassword, String source) {
        String sanitized = rawPassword.replaceAll("\\s+", "");
        if (sanitized.length() != 16) {
            throw new IllegalStateException(
                    "Invalid Gmail app password from " + source + ". " +
                    "Expected 16 characters (without spaces)."
            );
        }
        return sanitized;
    }
}
