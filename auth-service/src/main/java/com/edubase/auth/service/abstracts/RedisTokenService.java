package com.edubase.auth.service.abstracts;

public interface RedisTokenService {

    public void blacklistToken(String token,long expiration);

    public boolean isTokenBlacklisted(String token);

}
