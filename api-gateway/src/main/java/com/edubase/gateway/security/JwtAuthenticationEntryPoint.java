package com.edubase.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        String reason = jwtFailureReason(ex);
        log.warn("Unauthorized | method={} | path={} | reason={} | auth={} | msg={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                reason,
                authorizationSummary(exchange),
                ex.getMessage());
        return SecurityErrorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
    }

    private String authorizationSummary(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return "missing";
        }

        String trimmed = authorization.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            int separator = trimmed.indexOf(' ');
            String scheme = separator > 0 ? trimmed.substring(0, separator) : trimmed;
            return "non-bearer scheme=" + scheme;
        }

        String token = trimmed.substring(7).trim();
        if (token.isBlank()) {
            return "empty-bearer";
        }

        long dotCount = token.chars().filter(ch -> ch == '.').count();
        return "bearer length=" + token.length()
                + " parts=" + (dotCount + 1)
                + " sha256_12=" + sha256Prefix(token);
    }

    private String jwtFailureReason(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("expired")) {
                    return "expired token";
                }
                if (normalized.contains("issuer")) {
                    return "invalid issuer";
                }
                if (normalized.contains("audience")) {
                    return "invalid audience";
                }
                if (normalized.contains("signature") || normalized.contains("mac")) {
                    return "invalid signature";
                }
                if (normalized.contains("blacklisted")) {
                    return "blacklisted token";
                }
                if (normalized.contains("malformed") || normalized.contains("jwt") || normalized.contains("token")) {
                    return "invalid token";
                }
            }
            current = current.getCause();
        }
        return ex.getClass().getSimpleName();
    }

    private String sha256Prefix(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                builder.append(String.format("%02x", digest[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "unavailable";
        }
    }
}
