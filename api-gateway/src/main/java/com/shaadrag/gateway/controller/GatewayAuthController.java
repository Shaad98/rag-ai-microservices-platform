package com.shaadrag.gateway.controller;

import com.shaadrag.gateway.client.IdentityClient;
import com.shaadrag.gateway.dto.request.LoginRequest;
import com.shaadrag.gateway.dto.response.AuthResponse;
import com.shaadrag.gateway.dto.response.LoginResponse;
import com.shaadrag.gateway.service.CsrfTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

 @RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class GatewayAuthController {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String REFRESH_COOKIE = "refresh_token";

    private final CsrfTokenService csrfTokenService;
    private final IdentityClient identityClient;

    /*
     * Generates a CSRF token, stores it in Redis,
     * and sends the same token to the browser as a cookie.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(
            HttpServletResponse response) {

        String csrfToken = csrfTokenService.create();

        // CSRF token is readable by frontend, so HttpOnly = false
        addCookie(
                response,
                CSRF_COOKIE,
                csrfToken,
                false,
                Duration.ofDays(1));

        return ResponseEntity.ok(
                Map.of("token", csrfToken));
    }

    /*
     * Refreshes the access token.
     *
     * Gateway first validates CSRF.
     * If valid, Gateway sends the refresh token to Identity.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request) {

        // CSRF validation happens before calling Identity
        if (!isCsrfValid(request)) {
            return ResponseEntity.status(403).build();
        }

        // Read refresh token from HttpOnly browser cookie
        String refreshToken = extractCookie(request, REFRESH_COOKIE);

        if (refreshToken == null) {
            return ResponseEntity.status(401).build();
        }

        /*
         * Gateway does not validate the refresh token itself.
         * Identity validates it using its Redis.
         */
        ResponseEntity<AuthResponse> identityResponse = identityClient.refresh(
                REFRESH_COOKIE + "=" + refreshToken);

        return identityResponse;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response) {

        ResponseEntity<LoginResponse> identityResponse = identityClient.login(request);

        LoginResponse body = identityResponse.getBody();

        if (body == null) {
            return ResponseEntity.status(500).build();
        }

        addCookie(
                response,
                REFRESH_COOKIE,
                body.refreshToken(),
                true,
                Duration.ofDays(1));

        return ResponseEntity.ok(
                new LoginResponse(body.accessToken(),body.refreshToken()));
    }

    /*
     * Logs the user out.
     *
     * Gateway:
     * 1. Validates CSRF
     * 2. Sends refresh token to Identity
     * 3. Deletes CSRF token from Redis
     * 4. Deletes browser cookies
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Prevent cross-site logout requests
        if (!isCsrfValid(request)) {
            return ResponseEntity.status(403).build();
        }

        // Get refresh token from browser cookie
        String refreshToken = extractCookie(request, REFRESH_COOKIE);

        // Get CSRF token from request header
        String csrfToken = request.getHeader(CSRF_HEADER);

        if (refreshToken == null) {
            return ResponseEntity.status(401).build();
        }

        /*
         * Identity is responsible for invalidating
         * the refresh token from its Redis.
         */
        ResponseEntity<Void> identityResponse = identityClient.logout(
                REFRESH_COOKIE + "=" + refreshToken);

        // Do not clear local/browser state if Identity failed
        if (!identityResponse.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity
                    .status(identityResponse.getStatusCode())
                    .build();
        }

        // Remove CSRF token from Gateway Redis
        csrfTokenService.delete(csrfToken);

        // Remove refresh token from browser
        deleteCookie(response, REFRESH_COOKIE, true);

        // Remove CSRF token from browser
        deleteCookie(response, CSRF_COOKIE, false);

        return ResponseEntity.noContent().build();
    }

    /*
     * Validates the CSRF token sent in the request header
     * against the token stored in Redis.
     */
    // private boolean isCsrfValid(
    // HttpServletRequest request) {

    // String headerToken =
    // request.getHeader(CSRF_HEADER);

    // return csrfTokenService.validate(headerToken);
    // }

    /**
     * Validates the CSRF token sent by the frontend.
     *
     * The token must:
     * 1. Exist in the X-XSRF-TOKEN request header.
     * 2. Match the XSRF-TOKEN cookie.
     * 3. Exist in Gateway Redis.
     */
    private boolean isCsrfValid(HttpServletRequest request) {

        String headerToken = request.getHeader(CSRF_HEADER);

        String cookieToken = extractCookie(request, CSRF_COOKIE);

        return headerToken != null
                && headerToken.equals(cookieToken)
                && csrfTokenService.validate(headerToken);
    }

    /*
     * Finds a cookie by name and returns its value.
     */
    private String extractCookie(
            HttpServletRequest request,
            String name) {

        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {

            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    /*
     * Creates and sends a cookie to the browser.
     */
    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            boolean httpOnly,
            Duration maxAge) {

        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString());
    }

    /*
     * Deletes a cookie by setting its Max-Age to zero.
     */
    private void deleteCookie(
            HttpServletResponse response,
            String name,
            boolean httpOnly) {

        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(httpOnly)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString());
    }
}