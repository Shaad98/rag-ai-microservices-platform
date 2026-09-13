package com.shaadrag.document.service;

import com.shaadrag.document.security.JwtAuthenticationException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final PublicKey publicKey;

    public Claims extractAllClaims(
            String token
    ) {

        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(
            Claims claims
    ) {

        return claims.getSubject();
    }

    public String extractEmail(
            Claims claims
    ) {

        return claims.get(
                "email",
                String.class
        );
    }

    public String extractRole(
            Claims claims
    ) {

        return claims.get(
                "role",
                String.class
        );
    }

    public void validateClaims(
            Claims claims
    ) {

        Date expiration =
                claims.getExpiration();

        if (expiration == null) {

            throw new JwtAuthenticationException(
                    "JWT expiration is missing"
            );
        }

        if (expiration.before(new Date())) {

            throw new JwtAuthenticationException(
                    "JWT token has expired"
            );
        }

        String userId =
                claims.getSubject();

        if (userId == null ||
                userId.isBlank()) {

            throw new JwtAuthenticationException(
                    "JWT user ID is missing"
            );
        }

        String role =
                extractRole(claims);

        if (role == null ||
                role.isBlank()) {

            throw new JwtAuthenticationException(
                    "JWT role is missing"
            );
        }
    }
}