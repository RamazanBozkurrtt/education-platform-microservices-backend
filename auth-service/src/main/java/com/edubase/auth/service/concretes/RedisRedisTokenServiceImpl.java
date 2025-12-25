package com.edubase.auth.service.concretes;

import com.edubase.auth.service.abstracts.RedisTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisRedisTokenServiceImpl implements RedisTokenService {

    private final RedisTemplate<String,String> redisTemplate;

    @Override
    public void blacklistToken(String token, long expiration) {
        long ttl = expiration-System.currentTimeMillis();
        if(ttl>0){
            redisTemplate.opsForValue().set(token,"blacklisted", Duration.ofMillis(ttl));
        }
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        return redisTemplate.hasKey(token);
    }
}
