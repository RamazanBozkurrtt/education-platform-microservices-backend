package com.edubase.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimitConfig {

    private final ReactiveJwtDecoder jwtDecoder;

    public RateLimitConfig(ReactiveJwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Bean("rateLimitKeyResolver")
    public KeyResolver rateLimitKeyResolver() {
        return this::resolveRateLimitKey;
    }

    private Mono<String> resolveRateLimitKey(ServerWebExchange exchange) {
        String token = extractBearerToken(exchange);
        if (!StringUtils.hasText(token)) {
            return Mono.just(resolveClientIp(exchange));
        }

        return jwtDecoder.decode(token)
                .map(jwt -> {
                    String userIdFromClaim = jwt.getClaimAsString("user_id");
                    if (StringUtils.hasText(userIdFromClaim)) {
                        return userIdFromClaim.trim();
                    }

                    if (StringUtils.hasText(jwt.getSubject())) {
                        return jwt.getSubject().trim();
                    }

                    if (StringUtils.hasText(jwt.getId())) {
                        return jwt.getId().trim();
                    }
                    return resolveClientIp(exchange);
                })
                .onErrorResume(ex -> Mono.just(resolveClientIp(exchange)));
    }

    private String extractBearerToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7).trim();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown-client";
        }

        return remoteAddress.getAddress().getHostAddress();
    }
}
