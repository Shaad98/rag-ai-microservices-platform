package com.shaadrag.gateway.service;

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

    public Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {

        return extractAllClaims(token)
                .getSubject();
    }

    public String extractEmail(String token) {

        return extractAllClaims(token)
                .get("email", String.class);
    }

    public String extractRole(String token) {

        return extractAllClaims(token)
                .get("role", String.class);
    }

    public boolean isTokenValid(String token) {

        try {

            Claims claims =
                    extractAllClaims(token);

            Date expiration =
                    claims.getExpiration();

            return expiration != null
                    && expiration.after(new Date());

        } catch (Exception e) {

            return false;
        }
    }
}