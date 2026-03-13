package com.edubase.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class JwtAccessDeniedHandler implements ServerAccessDeniedHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
        log.warn("Forbidden | method={} | path={} | msg={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                ex.getMessage());
        return SecurityErrorWriter.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }
}
