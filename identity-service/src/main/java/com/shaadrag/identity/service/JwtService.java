package com.shaadrag.identity.service;

import com.shaadrag.identity.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;

@Service
@AllArgsConstructor
public class JwtService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    private static final long ACCESS_TOKEN_EXPIRATION =
            10 * 60 * 1000L;

    // =========================================================
    // GENERATE ACCESS TOKEN
    // =========================================================

    public String generateAccessToken(
            UserDetails userDetails) {

        User user =
                (User) userDetails;

        return Jwts.builder()

                .subject(
                        user.getUserId()
                )

                .claim(
                        "email",
                        user.getEmail()
                )

                .claim(
                        "role",
                        user.getRole().name()
                )

                .issuedAt(
                        new Date()
                )

                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + ACCESS_TOKEN_EXPIRATION
                        )
                )

                .signWith(
                        privateKey
                )

                .compact();
    }

    // =========================================================
    // EXTRACT CLAIMS
    // =========================================================

    public Claims extractAllClaims(
            String token) {

        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(
            String token) {

        return extractAllClaims(token)
                .getSubject();
    }

    public String extractEmail(
            String token) {

        return extractAllClaims(token)
                .get(
                        "email",
                        String.class
                );
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    public boolean isTokenValid(
            String token,
            UserDetails userDetails) {

        try {

            User user =
                    (User) userDetails;

            String userId =
                    extractUserId(token);

            String email =
                    extractEmail(token);

            return userId.equals(
                        user.getUserId()
                    )
                    && email.equals(
                        user.getEmail()
                    )
                    && !isTokenExpired(token);

        } catch (Exception e) {

            return false;
        }
    }

    private boolean isTokenExpired(
            String token) {

        return extractExpiration(token)
                .before(new Date());
    }

    private Date extractExpiration(
            String token) {

        return extractAllClaims(token)
                .getExpiration();
    }
}