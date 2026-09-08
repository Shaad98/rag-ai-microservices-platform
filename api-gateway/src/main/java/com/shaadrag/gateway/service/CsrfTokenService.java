package com.shaadrag.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CsrfTokenService {

    private static final String KEY_PREFIX = "csrf:";
    private static final Duration TTL = Duration.ofDays(3);

    private final StringRedisTemplate redisTemplate;

    public String create() {

        String token = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
                buildKey(token),
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
                redisTemplate.hasKey(buildKey(token))
        );
    }

    public void delete(String token) {

        if (token == null || token.isBlank()) {
            return;
        }

        redisTemplate.delete(buildKey(token));
    }

    private String buildKey(String token) {
        return KEY_PREFIX + token;
    }
}