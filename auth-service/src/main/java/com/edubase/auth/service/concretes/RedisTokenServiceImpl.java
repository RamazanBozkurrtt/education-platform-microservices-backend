package com.edubase.auth.service.concretes;

import com.edubase.auth.jwt.JwtService;
import com.edubase.auth.service.abstracts.RedisTokenService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisTokenServiceImpl implements RedisTokenService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final JwtService jwtService;

    @Override
    public void blacklistToken(String token, long expiresAtMillis) {
        long ttlMillis = expiresAtMillis - System.currentTimeMillis();
        if (ttlMillis <= 0) return;

        redisTemplate.opsForValue()
                .set(key(token), "1", Duration.ofMillis(ttlMillis));
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        try {
            return redisTemplate.hasKey(key(token));
        }catch (Exception e){
            return true;
        }
    }

    private String key(String token) {
        return BLACKLIST_PREFIX + jwtService.extractClaim(token, Claims::getId);
    }

}
