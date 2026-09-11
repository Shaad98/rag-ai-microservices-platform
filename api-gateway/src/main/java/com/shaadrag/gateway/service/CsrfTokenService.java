package com.shaadrag.gateway.service;

import com.shaadrag.gateway.exception.RedisUnavailableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsrfTokenService {

    private static final String KEY_PREFIX = "csrf:";

    private static final Duration TTL =
            Duration.ofDays(3);

    private final StringRedisTemplate redisTemplate;

    public String create() {

        String token =
                UUID.randomUUID().toString();

        try {

            redisTemplate.opsForValue().set(
                    buildKey(token),
                    "valid",
                    TTL
            );

            return token;

        } catch (DataAccessException ex) {

            throw new RedisUnavailableException(
                    "Unable to create CSRF token because Redis is unavailable",
                    ex
            );
        }
    }

    public boolean validate(String token) {

        if (token == null || token.isBlank()) {
            return false;
        }

        try {

            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(
                            buildKey(token)
                    )
            );

        } catch (DataAccessException ex) {

            throw new RedisUnavailableException(
                    "Unable to validate CSRF token because Redis is unavailable",
                    ex
            );
        }
    }

    public void delete(String token) {

        if (token == null || token.isBlank()) {
            return;
        }

        try {

            redisTemplate.delete(
                    buildKey(token)
            );

        } catch (DataAccessException ex) {

            log.warn(
                    "Unable to delete CSRF token from Redis",
                    ex
            );
        }
    }

    private String buildKey(String token) {

        return KEY_PREFIX + token;
    }
}