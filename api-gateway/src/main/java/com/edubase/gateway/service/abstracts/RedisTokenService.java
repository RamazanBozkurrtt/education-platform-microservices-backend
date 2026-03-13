package com.edubase.gateway.service.abstracts;

public interface RedisTokenService {

    public boolean isTokenBlacklisted(String token);


}
