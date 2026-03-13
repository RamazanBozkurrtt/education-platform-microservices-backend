package com.edubase.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        log.warn("Unauthorized | method={} | path={} | msg={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                ex.getMessage());
        return SecurityErrorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
    }
}
