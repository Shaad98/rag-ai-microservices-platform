package com.shaadrag.identity.service;

import com.shaadrag.identity.model.User;
import com.shaadrag.identity.security.JwtAuthenticationException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final PrivateKey privateKey;

    private final PublicKey publicKey;

    private static final long ACCESS_TOKEN_EXPIRATION =
            10 * 60 * 1000L;

    /*
     * =========================================================
     * GENERATE ACCESS TOKEN
     * =========================================================
     */

    public String generateAccessToken(
            UserDetails userDetails
    ) {

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

    /*
     * =========================================================
     * EXTRACT CLAIMS
     * =========================================================
     */

    public Claims extractAllClaims(
            String token
    ) {

        try {

            /*
             * Possible JWT parsing exceptions:
             *
             * ExpiredJwtException
             * MalformedJwtException
             * SignatureException
             * UnsupportedJwtException
             * IllegalArgumentException
             */

            return Jwts.parser()

                    .verifyWith(
                            publicKey
                    )

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

    /*
     * =========================================================
     * EXTRACT CLAIMS FROM ALREADY PARSED JWT
     * =========================================================
     */

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

    /*
     * =========================================================
     * VALIDATE CLAIMS
     * =========================================================
     */

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

        String email =
                extractEmail(claims);

        if (email == null ||
            email.isBlank()) {

            throw new JwtAuthenticationException(
                    "JWT email is missing"
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

    /*
     * =========================================================
     * VALIDATE JWT AGAINST DATABASE USER
     * =========================================================
     */

    public boolean isTokenValid(
            Claims claims,
            UserDetails userDetails
    ) {

        User user =
                (User) userDetails;

        String userId =
                extractUserId(claims);

        String email =
                extractEmail(claims);

        return userId.equals(
                    user.getUserId()
                )
                && email.equals(
                    user.getEmail()
                );
    }
}