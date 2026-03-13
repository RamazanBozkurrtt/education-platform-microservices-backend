package com.edubase.gateway.service.concretes;

import com.edubase.gateway.service.abstracts.RedisTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisTokenServiceImpl implements RedisTokenService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:";


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
    }//JWT İD Sİ SET LEDİN YARIN BÜTÜN CLAİMS EXTRAXTLARI DEĞİŞTİR CLAİMS İÇERİSİNE USERNAME YOK İD LERİ jti DEN AL. bURAYI DA TAMAMLA


}
