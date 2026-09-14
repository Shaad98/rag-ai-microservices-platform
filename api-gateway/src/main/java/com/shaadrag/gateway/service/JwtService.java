package com.shaadrag.gateway.service;

import com.shaadrag.gateway.security.JwtAuthenticationException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final PublicKey publicKey;


        // ExpiredJwtException
        // MalformedJwtException
        // SignatureException
        // UnsupportedJwtException

    // =========================================================
    // EXTRACT CLAIMS
    // =========================================================

    public Claims extractAllClaims(
            String token
    ) {

        try {

            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (JwtException |
                 IllegalArgumentException ex) {

            throw new JwtAuthenticationException(
                    "Invalid or expired JWT token",
                    ex
            );
        }
    }

    // =========================================================
    // EXTRACT USER ID
    // =========================================================

    public String extractUserId(
            Claims claims
    ) {

        return claims.getSubject();
    }

    // =========================================================
    // EXTRACT EMAIL
    // =========================================================

    public String extractEmail(
            Claims claims
    ) {

        return claims.get(
                "email",
                String.class
        );
    }

    // =========================================================
    // EXTRACT ROLE
    // =========================================================

    public String extractRole(
            Claims claims
    ) {

        return claims.get(
                "role",
                String.class
        );
    }

    // =========================================================
    // VALIDATE CLAIMS
    // =========================================================

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
                extractUserId(claims);

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