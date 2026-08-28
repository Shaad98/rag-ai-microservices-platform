package com.shaadrag.gateway.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class CsrfTokenService {

    private static final String KEY_PREFIX = "csrf:";
    private static final Duration TTL =
            Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;

    public CsrfTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String create() {

        String token = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
            KEY_PREFIX + token,
            "valid",
            TTL
        );

        return token;
    }

    public boolean validate(String token) {

        if (token == null || token.isBlank()) {
            return false;
        }

        return Boolean.TRUE.equals(
            redisTemplate.hasKey(KEY_PREFIX + token)
        );
    }

    public void delete(String token) {

        if (token == null || token.isBlank()) {
            return;
        }

        redisTemplate.delete(KEY_PREFIX + token);
    }
}