package com.shaadrag.document.security;

import com.shaadrag.document.service.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

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

        /*
         * No Authorization header.
         *
         * Don't reject here.
         * Spring Security will decide whether
         * authentication is required.
         */
        if (authHeader == null || authHeader.isBlank()) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }

        /*
         * Authorization header exists,
         * but does not contain Bearer authentication.
         */
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

        /*
         * Bearer exists but token is empty.
         */
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

            /*
             * Parse JWT once.
             */
            Claims claims =
                    jwtService.extractAllClaims(token);

            /*
             * Validate required claims.
             */
            jwtService.validateClaims(claims);

            String userId =
                    jwtService.extractUserId(claims);

            String role =
                    jwtService.extractRole(claims);

            SimpleGrantedAuthority authority =
                    new SimpleGrantedAuthority(
                            "ROLE_" + role
                    );

            /*
             * Create authenticated user.
             */
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

            /*
             * Store authentication.
             */
            SecurityContextHolder
                    .getContext()
                    .setAuthentication(
                            authentication
                    );

            /*
             * Continue request.
             */
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

        } catch (JwtException | IllegalArgumentException ex) {

            SecurityContextHolder.clearContext();

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new JwtAuthenticationException(
                            "Invalid or expired JWT token",
                            ex
                    )
            );
        }
    }
}