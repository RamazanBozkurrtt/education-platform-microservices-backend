package com.edubase.auth.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Getter
@Slf4j
public class GmailCredentialsProvider {

    private final ObjectMapper objectMapper;
    private final GmailProperties gmailProperties;

    private String username;
    private String password;
    private Path resolvedKeysPath;

    @PostConstruct
    void load() {
        Path keysPath = resolveKeysPath();
        this.resolvedKeysPath = keysPath;
        JsonNode root = readJson(keysPath);
        JsonNode gmailNode = root.path("gmail");

        if (!gmailNode.isObject()) {
            throw new IllegalStateException("keys.json must include an object field named 'gmail'.");
        }

        String resolvedUsername = firstNonBlank(gmailNode, "username", "mail", "email");
        String resolvedPassword = firstNonBlank(gmailNode, "app-password", "appPassword", "password", "app_password");

        if (resolvedUsername == null) {
            throw new IllegalStateException("Gmail username/mail is missing under keys.json -> gmail.");
        }
        if (resolvedPassword == null) {
            throw new IllegalStateException("Gmail app password is missing under keys.json -> gmail.");
        }

        this.username = resolvedUsername;
        this.password = resolvedPassword.replaceAll("\\s+", "");
        log.info("Gmail credentials loaded from {}", keysPath);
    }

    private Path resolveKeysPath() {
        String configuredPath = gmailProperties.getKeysFile();
        List<Path> candidates = new ArrayList<>();

        if (configuredPath != null && !configuredPath.isBlank()) {
            Path configured = Path.of(configuredPath);
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

        throw new IllegalStateException("keys.json file not found. Checked: " + candidates);
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
}
