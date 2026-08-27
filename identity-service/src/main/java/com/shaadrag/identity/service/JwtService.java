package com.shaadrag.identity.service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.Map;

// import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.shaadrag.identity.model.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.AllArgsConstructor;

@AllArgsConstructor

@Service
public class JwtService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    // @Value("${jwt.access-token-expiration}")
    // private long accessTokenExpiration;

    private final long accessTokenExpiration = 10 * 60 * 1000;

    // public JwtService(
    // PrivateKey privateKey,
    // PublicKey publicKey) {

    // this.privateKey = privateKey;
    // this.publicKey = publicKey;
    // }

    // public String generateAccessToken(
    // UserDetails userDetails) {

    // return generateAccessToken(
    // Map.of(),
    // userDetails.getUsername()
    // );
    // }

    // public String generateAccessToken(
    // Map<String, Object> extraClaims,
    // String username) {

    // Date now = new Date();

    // Date expiration =
    // new Date(
    // now.getTime()
    // + accessTokenExpiration
    // );

    // return Jwts.builder()
    // .claims(extraClaims)
    // .subject(username)
    // .issuedAt(now)
    // .expiration(expiration)
    // .signWith(
    // privateKey,
    // Jwts.SIG.RS256
    // )
    // .compact();
    // }

    public String generateAccessToken(UserDetails userDetails) {

        User user = (User) userDetails;

        return Jwts.builder()
                .subject(user.getUserId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + this.accessTokenExpiration))
                .signWith(privateKey)
                .compact();
    }

    public String extractUserId(String token) {

        return extractAllClaims(token)
                .getSubject();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token)
                .get("email", String.class);
    }

    public Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // public boolean isTokenValid(
    // String token,
    // UserDetails userDetails) {

    // try {

    // String username = extractEmail(token);

    // return username.equals(
    // userDetails.getUsername())
    // && !isTokenExpired(token);

    // } catch (Exception e) {

    // return false;
    // }
    // }
    
    public boolean isTokenValid(
            String token,
            UserDetails userDetails) {

        try {
            User user = (User) userDetails;

            String userId = extractUserId(token);
            String email = extractEmail(token);

            return userId.equals(user.getUserId())
                    && email.equals(user.getEmail())
                    && !isTokenExpired(token);

        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {

        return extractExpiration(token)
                .before(new Date());
    }

    private Date extractExpiration(String token) {

        return extractAllClaims(token)
                .getExpiration();
    }
}