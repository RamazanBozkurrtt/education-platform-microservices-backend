package com.edubase.gateway.service.concretes;

import com.edubase.gateway.service.abstracts.RedisTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisTokenServiceImpl implements RedisTokenService {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:";

    @Override
    public Mono<Void> blacklistToken(String tokenId, long expiresAtMillis) {
        long ttlMillis = expiresAtMillis - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return Mono.empty();
        }

        return redisTemplate.opsForValue()
                .set(key(tokenId), "1", Duration.ofMillis(ttlMillis))
                .then();
    }

    @Override
    public Mono<Boolean> isTokenBlacklisted(String tokenId) {
        return redisTemplate.hasKey(key(tokenId))
                .onErrorReturn(true);
    }

    private String key(String tokenId) {
        return BLACKLIST_PREFIX + tokenId;
    }
}
