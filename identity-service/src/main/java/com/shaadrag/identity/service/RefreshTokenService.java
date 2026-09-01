package com.shaadrag.identity.service;

import com.shaadrag.identity.model.RefreshTokenData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(1);

    private final RedisTemplate<String, RefreshTokenData> refreshTokenRedisTemplate;

    public String createRefreshToken(String userId) {

        String refreshToken = UUID.randomUUID().toString();

        RefreshTokenData tokenData =
                new RefreshTokenData(userId);

        refreshTokenRedisTemplate.opsForValue()
                .set(
                        buildKey(refreshToken),
                        tokenData,
                        REFRESH_TOKEN_TTL
                );

        return refreshToken;
    }

    public RefreshTokenData validateRefreshToken(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }

        return refreshTokenRedisTemplate.opsForValue()
                .get(buildKey(refreshToken));
    }

    public void deleteRefreshToken(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        refreshTokenRedisTemplate.delete(
                buildKey(refreshToken)
        );
    }

    private String buildKey(String refreshToken) {
        return REFRESH_TOKEN_PREFIX + refreshToken;
    }
}