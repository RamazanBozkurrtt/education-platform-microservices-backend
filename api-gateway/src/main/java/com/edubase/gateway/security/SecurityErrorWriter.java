package com.edubase.gateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

final class SecurityErrorWriter {

    private SecurityErrorWriter() {
    }

    static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        String safeMessage = sanitize(message);
        String path = exchange.getRequest().getPath().value();
        String body = "{\"error\":\"" + code + "\",\"message\":\"" + safeMessage + "\",\"path\":\"" + path + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "'");
    }
}
