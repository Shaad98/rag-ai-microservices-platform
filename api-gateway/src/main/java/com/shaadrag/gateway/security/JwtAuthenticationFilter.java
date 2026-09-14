package com.shaadrag.gateway.security;

import com.shaadrag.gateway.service.JwtService;

import io.jsonwebtoken.Claims;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtService jwtService;

    private final AuthenticationEntryPoint
            authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader =
                request.getHeader("Authorization");

        // =====================================================
        // NO AUTHORIZATION HEADER
        // =====================================================

        if (authHeader == null ||
            authHeader.isBlank()) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }

        // =====================================================
        // INVALID AUTHORIZATION HEADER
        // =====================================================

        if (!authHeader.startsWith("Bearer ")) {

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new JwtAuthenticationException(
                            "Invalid Authorization header"
                    )
            );

            return;
        }

        String token =
                authHeader.substring(7).trim();

        // =====================================================
        // EMPTY TOKEN
        // =====================================================

        if (token.isBlank()) {

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new JwtAuthenticationException(
                            "Bearer token is missing"
                    )
            );

            return;
        }

        try {

            // =================================================
            // PARSE JWT
            // =================================================

            Claims claims =
                    jwtService.extractAllClaims(token);

            // =================================================
            // VALIDATE CLAIMS
            // =================================================

            jwtService.validateClaims(claims);

            String userId =
                    jwtService.extractUserId(claims);

            String role =
                    jwtService.extractRole(claims);

            // =================================================
            // CREATE AUTHENTICATION
            // =================================================

            SimpleGrantedAuthority authority =
                    new SimpleGrantedAuthority(
                            "ROLE_" + role
                    );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(authority)
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request)
            );

            // =================================================
            // STORE AUTHENTICATION
            // =================================================

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

            // =================================================
            // CONTINUE
            // =================================================

            filterChain.doFilter(
                    request,
                    response
            );

        } catch (JwtAuthenticationException ex) {

            SecurityContextHolder.clearContext();

            authenticationEntryPoint.commence(
                    request,
                    response,
                    ex
            );
        }
    }
}