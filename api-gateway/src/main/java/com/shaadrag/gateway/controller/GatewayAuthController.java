package com.shaadrag.gateway.controller;

import com.shaadrag.gateway.client.IdentityClient;
import com.shaadrag.gateway.dto.request.LoginRequest;
import com.shaadrag.gateway.dto.response.ErrorResponse;
import com.shaadrag.gateway.dto.response.IdentityLoginResponse;
import com.shaadrag.gateway.dto.response.LoginResponse;
import com.shaadrag.gateway.dto.response.RefreshTokenResponse;
import com.shaadrag.gateway.exception.IdentityServiceException;
import com.shaadrag.gateway.service.CsrfTokenService;

import feign.FeignException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class GatewayAuthController {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private static final String REFRESH_COOKIE = "refresh_token";

    private static final Duration CSRF_TTL =
            Duration.ofDays(3);

    private static final Duration REFRESH_TTL =
            Duration.ofDays(1);

    private final CsrfTokenService csrfTokenService;

    private final IdentityClient identityClient;


    // =========================================================
    // CSRF TOKEN
    // =========================================================

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(
            HttpServletRequest request,
            HttpServletResponse response) {

        String csrfToken =
                ensureCsrfToken(request, response);

        return ResponseEntity.ok(
                Map.of("token", csrfToken)
        );
    }


    // =========================================================
    // LOGIN
    // =========================================================

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        // Make sure CSRF token exists
        ensureCsrfToken(
                httpRequest,
                httpResponse
        );

        try {

            ResponseEntity<IdentityLoginResponse>
                    identityResponse =
                    identityClient.login(request);


            // -------------------------------------------------
            // SUCCESS
            // -------------------------------------------------

            if (identityResponse
                    .getStatusCode()
                    .is2xxSuccessful()
                    && identityResponse.getBody() != null) {

                IdentityLoginResponse body =
                        identityResponse.getBody();


                // Store refresh token in HttpOnly cookie
                addCookie(
                        httpResponse,
                        REFRESH_COOKIE,
                        body.refreshToken(),
                        true,
                        REFRESH_TTL
                );


                // Return only access token to frontend
                return ResponseEntity.ok(
                        new LoginResponse(
                                body.accessToken()
                        )
                );
            }


            // -------------------------------------------------
            // Unexpected response
            // -------------------------------------------------

            return buildResponse(
                    HttpStatus.BAD_GATEWAY,
                    "IDENTITY_SERVICE_ERROR",
                    "Authentication service returned an unexpected response",
                    httpRequest
            );


        } catch (IdentityServiceException ex) {

            // -------------------------------------------------
            // Identity returned structured error
            // -------------------------------------------------

            if (ex.getErrorResponse() != null) {

                return ResponseEntity
                        .status(ex.getStatus())
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .body(
                                ex.getErrorResponse()
                        );
            }


            // -------------------------------------------------
            // Identity returned error without body
            // -------------------------------------------------

            return buildResponse(
                    HttpStatus.BAD_GATEWAY,
                    "IDENTITY_SERVICE_ERROR",
                    "Authentication service returned an error",
                    httpRequest
            );


        } catch (FeignException ex) {

            // -------------------------------------------------
            // Communication / Feign failure
            // -------------------------------------------------

            return buildResponse(
                    HttpStatus.BAD_GATEWAY,
                    "IDENTITY_SERVICE_UNAVAILABLE",
                    "Authentication service is currently unavailable",
                    httpRequest
            );
        }
    }


    // =========================================================
    // REFRESH TOKEN
    // =========================================================

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        // -----------------------------------------------------
        // Validate CSRF
        // -----------------------------------------------------

        if (!isCsrfValid(request)) {

            terminateRefreshSession(
                    request,
                    response
            );

            return buildResponse(
                    HttpStatus.FORBIDDEN,
                    "CSRF_VALIDATION_FAILED",
                    "Invalid CSRF token",
                    request
            );
        }


        // -----------------------------------------------------
        // Get refresh token
        // -----------------------------------------------------

        String refreshToken =
                extractCookie(
                        request,
                        REFRESH_COOKIE
                );


        if (refreshToken == null) {

            terminateRefreshSession(
                    request,
                    response
            );

            return buildResponse(
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_MISSING",
                    "Refresh token is missing",
                    request
            );
        }


        try {

            ResponseEntity<RefreshTokenResponse>
                    identityResponse =
                    identityClient.refresh(
                            REFRESH_COOKIE
                                    + "="
                                    + refreshToken
                    );


            // -------------------------------------------------
            // SUCCESS
            // -------------------------------------------------

            if (identityResponse
                    .getStatusCode()
                    .is2xxSuccessful()) {

                return ResponseEntity
                        .status(
                                identityResponse
                                        .getStatusCode()
                        )
                        .body(
                                identityResponse.getBody()
                        );
            }


            // -------------------------------------------------
            // Unexpected Identity response
            // -------------------------------------------------

            return buildResponse(
                    HttpStatus.BAD_GATEWAY,
                    "IDENTITY_SERVICE_ERROR",
                    "Authentication service returned an unexpected response",
                    request
            );


        } catch (IdentityServiceException ex) {

            // -------------------------------------------------
            // Identity structured error
            // -------------------------------------------------

            if (ex.getStatus() == HttpStatus.UNAUTHORIZED.value()
                    || ex.getStatus() == HttpStatus.FORBIDDEN.value()) {

                terminateRefreshSession(
                        request,
                        response
                );
            }


            if (ex.getErrorResponse() != null) {

                return ResponseEntity
                        .status(ex.getStatus())
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .body(
                                ex.getErrorResponse()
                        );
            }


            return buildResponse(
                    HttpStatus.BAD_GATEWAY,
                    "IDENTITY_SERVICE_ERROR",
                    "Authentication service returned an error",
                    request
            );


        } catch (FeignException ex) {

            // -------------------------------------------------
            // Feign communication failure
            // -------------------------------------------------

            if (ex.status()
                    == HttpStatus.UNAUTHORIZED.value()
                    || ex.status()
                    == HttpStatus.FORBIDDEN.value()) {

                terminateRefreshSession(
                        request,
                        response
                );
            }


            return buildResponse(
                    HttpStatus.BAD_GATEWAY,
                    "IDENTITY_SERVICE_UNAVAILABLE",
                    "Authentication service is currently unavailable",
                    request
            );
        }
    }


    // =========================================================
    // LOGOUT
    // =========================================================

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        // -----------------------------------------------------
        // Validate CSRF
        // -----------------------------------------------------

        if (!isCsrfValid(request)) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .build();
        }


        // -----------------------------------------------------
        // Extract tokens
        // -----------------------------------------------------

        String refreshToken =
                extractCookie(
                        request,
                        REFRESH_COOKIE
                );

        String csrfToken =
                request.getHeader(
                        CSRF_HEADER
                );


        // -----------------------------------------------------
        // Tell Identity to logout
        // -----------------------------------------------------

        if (refreshToken != null) {

            try {

                identityClient.logout(
                        REFRESH_COOKIE
                                + "="
                                + refreshToken
                );

            } catch (FeignException ignored) {

                // Logout should remain idempotent
            }
        }


        // -----------------------------------------------------
        // Delete Gateway-side session data
        // -----------------------------------------------------

        csrfTokenService.delete(
                csrfToken
        );

        deleteCookie(
                response,
                REFRESH_COOKIE
        );

        deleteCookie(
                response,
                CSRF_COOKIE
        );


        return ResponseEntity
                .noContent()
                .build();
    }


    // =========================================================
    // BUILD ERROR RESPONSE
    // =========================================================

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {

        ErrorResponse response =
                new ErrorResponse(
                        LocalDateTime.now(),
                        status.value(),
                        code,
                        message,
                        request.getRequestURI()
                );

        return ResponseEntity
                .status(status)
                .contentType(
                        MediaType.APPLICATION_JSON
                )
                .body(response);
    }


    // =========================================================
    // ENSURE CSRF TOKEN
    // =========================================================

    private String ensureCsrfToken(
            HttpServletRequest request,
            HttpServletResponse response) {

        String existingToken =
                extractCookie(
                        request,
                        CSRF_COOKIE
                );


        // Existing token is valid
        if (existingToken != null
                && csrfTokenService.validate(
                        existingToken
                )) {

            return existingToken;
        }


        // Existing token is invalid
        if (existingToken != null) {

            csrfTokenService.delete(
                    existingToken
            );
        }


        // Create new token
        String newToken =
                csrfTokenService.create();


        addCookie(
                response,
                CSRF_COOKIE,
                newToken,
                false,
                CSRF_TTL
        );


        return newToken;
    }


    // =========================================================
    // VALIDATE CSRF
    // =========================================================

    private boolean isCsrfValid(
            HttpServletRequest request) {

        String headerToken =
                request.getHeader(
                        CSRF_HEADER
                );

        String cookieToken =
                extractCookie(
                        request,
                        CSRF_COOKIE
                );


        if (headerToken == null
                || cookieToken == null) {

            return false;
        }


        return headerToken.equals(
                cookieToken
        )
                && csrfTokenService.validate(
                        headerToken
                );
    }


    // =========================================================
    // TERMINATE REFRESH SESSION
    // =========================================================

    private void terminateRefreshSession(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken =
                extractCookie(
                        request,
                        REFRESH_COOKIE
                );

        String csrfCookie =
                extractCookie(
                        request,
                        CSRF_COOKIE
                );


        // -----------------------------------------------------
        // Invalidate refresh token in Identity
        // -----------------------------------------------------

        if (refreshToken != null) {

            try {

                identityClient.logout(
                        REFRESH_COOKIE
                                + "="
                                + refreshToken
                );

            } catch (FeignException ignored) {
            }
        }


        // -----------------------------------------------------
        // Delete CSRF session
        // -----------------------------------------------------

        csrfTokenService.delete(
                csrfCookie
        );


        // -----------------------------------------------------
        // Delete cookies
        // -----------------------------------------------------

        deleteCookie(
                response,
                REFRESH_COOKIE
        );

        deleteCookie(
                response,
                CSRF_COOKIE
        );
    }


    // =========================================================
    // EXTRACT COOKIE
    // =========================================================

    private String extractCookie(
            HttpServletRequest request,
            String name) {

        Cookie[] cookies =
                request.getCookies();


        if (cookies == null) {
            return null;
        }


        for (Cookie cookie : cookies) {

            if (name.equals(
                    cookie.getName()
            )) {

                return cookie.getValue();
            }
        }


        return null;
    }


    // =========================================================
    // ADD COOKIE
    // =========================================================

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            boolean httpOnly,
            Duration maxAge) {

        ResponseCookie cookie =
                ResponseCookie
                        .from(
                                name,
                                value
                        )
                        .httpOnly(
                                httpOnly
                        )
                        .secure(true)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(maxAge)
                        .build();


        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
    }


    // =========================================================
    // DELETE COOKIE
    // =========================================================

    private void deleteCookie(
            HttpServletResponse response,
            String name) {

        ResponseCookie cookie =
                ResponseCookie
                        .from(
                                name,
                                ""
                        )
                        .httpOnly(
                                REFRESH_COOKIE.equals(
                                        name
                                )
                        )
                        .secure(true)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(Duration.ZERO)
                        .build();


        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
    }
}