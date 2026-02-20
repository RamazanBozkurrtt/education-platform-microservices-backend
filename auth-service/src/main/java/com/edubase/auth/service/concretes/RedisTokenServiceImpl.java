package com.edubase.auth.service.concretes;

import com.edubase.auth.service.abstracts.RedisTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisTokenServiceImpl implements RedisTokenService {

    private final RedisTemplate<String,String> redisTemplate;
    private static final String BLACKLIST_PREFIX = "blacklist:";

    @Override
    public void blacklistToken(String token, long expiration) {
        long ttl = expiration-System.currentTimeMillis();
        if(ttl>0){
            String key = BLACKLIST_PREFIX+token;
            redisTemplate.opsForValue().set(key,"blacklisted", Duration.ofMillis(ttl));
        }
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        return redisTemplate.hasKey(BLACKLIST_PREFIX+token);
    }
}
