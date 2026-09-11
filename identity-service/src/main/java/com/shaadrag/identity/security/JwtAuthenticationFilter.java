package com.shaadrag.identity.security;

import com.shaadrag.identity.service.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtService jwtService;

    private final CustomUserDetailsService userDetailsService;

    private final AuthenticationEntryPoint authenticationEntryPoint;

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
        if (authHeader == null ||
            authHeader.isBlank()) {

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
             * Validate JWT claims.
             */
            jwtService.validateClaims(claims);

            /*
             * Extract email from already parsed claims.
             */
            String email =
                    jwtService.extractEmail(claims);

            /*
             * Don't authenticate twice.
             */
            if (SecurityContextHolder
                    .getContext()
                    .getAuthentication() == null) {

                /*
                 * Load user from database.
                 */
                UserDetails userDetails =
                        userDetailsService
                                .loadUserByUsername(email);

                /*
                 * Compare JWT user information
                 * with database user.
                 */
                if (jwtService.isTokenValid(
                        claims,
                        userDetails
                )) {

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    /*
                     * Store authenticated user.
                     */
                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(
                                    authentication
                            );
                }
            }

            /*
             * Continue request.
             */
            filterChain.doFilter(
                    request,
                    response
            );

        } catch (JwtAuthenticationException ex) {

            /*
             * JWT authentication failed.
             */
            SecurityContextHolder.clearContext();

            authenticationEntryPoint.commence(
                    request,
                    response,
                    ex
            );

        } catch (AuthenticationException ex) {

            /*
             * User authentication failed,
             * for example user not found.
             */
            SecurityContextHolder.clearContext();

            authenticationEntryPoint.commence(
                    request,
                    response,
                    ex
            );

        } catch (JwtException |
                 IllegalArgumentException ex) {

            /*
             * Unexpected JWT parsing failure.
             */
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