package com.edubase.gateway.service.abstracts;

import reactor.core.publisher.Mono;

public interface RedisTokenService {

    Mono<Void> blacklistToken(String tokenId, long expiresAtMillis);

    Mono<Boolean> isTokenBlacklisted(String tokenId);

}
