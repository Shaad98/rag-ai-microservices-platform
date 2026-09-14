package com.shaadrag.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.shaadrag.gateway.client.IdentityClient;

import com.shaadrag.gateway.dto.request.LoginRequest;

import com.shaadrag.gateway.dto.response.ErrorResponse;
import com.shaadrag.gateway.dto.response.IdentityLoginResponse;
import com.shaadrag.gateway.dto.response.LoginResponse;
import com.shaadrag.gateway.dto.response.RefreshTokenResponse;

// import com.shaadrag.gateway.handler.ErrorResponseWriter;

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

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
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

    private final ObjectMapper objectMapper;

    // =========================================================
    // 1. GET CSRF
    // =========================================================

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        String csrfToken =
                ensureCsrfToken(
                        request,
                        response
                );

        return ResponseEntity.ok(
                Map.of(
                        "token",
                        csrfToken
                )
        );
    }

    // =========================================================
    // 2. LOGIN
    // =========================================================

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {

        /*
         * Login does not strictly require CSRF protection because
         * credentials are explicitly supplied by the frontend.
         *
         * We still establish the CSRF token so the browser is
         * ready for refresh/logout.
         */

        ensureCsrfToken(
                httpRequest,
                httpResponse
        );

        try {

            ResponseEntity<IdentityLoginResponse>
                    identityResponse =
                    identityClient.login(request);

            /*
             * Successful response from Identity.
             */

            if (identityResponse.getStatusCode().is2xxSuccessful()
                    && identityResponse.getBody() != null) {

                IdentityLoginResponse body =
                        identityResponse.getBody();

                /*
                 * Refresh token NEVER goes to frontend JSON.
                 *
                 * Store it only inside HttpOnly cookie.
                 */

                addCookie(
                        httpResponse,
                        REFRESH_COOKIE,
                        body.refreshToken(),
                        true,
                        REFRESH_TTL
                );

                /*
                 * Return only access token to frontend.
                 */

                return ResponseEntity.ok(
                        new LoginResponse(
                                body.accessToken()
                        )
                );
            }

            /*
             * Normally Feign throws FeignException
             * for non-2xx responses.
             */

            return ResponseEntity
                    .status(
                            identityResponse.getStatusCode()
                    )
                    .body(
                            identityResponse.getBody()
                    );

        } catch (FeignException ex) {

            return buildFeignErrorResponse(
                    ex,
                    httpRequest
            );
        }
    }

    // =========================================================
    // 3. REFRESH
    // =========================================================

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        /*
         * Refresh is CSRF protected because the browser
         * automatically sends the HttpOnly refresh cookie.
         */

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

        String refreshToken =
                extractCookie(
                        request,
                        REFRESH_COOKIE
                );

        /*
         * No refresh token means there is no session
         * that can be refreshed.
         */

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

            /*
             * Successful refresh.
             */

            if (identityResponse.getStatusCode().is2xxSuccessful()) {

                return ResponseEntity
                        .status(
                                identityResponse.getStatusCode()
                        )
                        .body(
                                identityResponse.getBody()
                        );
            }

            /*
             * Normally Feign throws before reaching here.
             */

            if (identityResponse.getStatusCode()
                    == HttpStatus.UNAUTHORIZED
                    || identityResponse.getStatusCode()
                    == HttpStatus.FORBIDDEN) {

                terminateRefreshSession(
                        request,
                        response
                );
            }

            return ResponseEntity
                    .status(
                            identityResponse.getStatusCode()
                    )
                    .body(
                            identityResponse.getBody()
                    );

        } catch (FeignException ex) {

            /*
             * Identity returned an authentication/session error.
             *
             * 401 / 403 means the refresh session is no longer usable.
             */

            if (ex.status()
                    == HttpStatus.UNAUTHORIZED.value()
                    || ex.status()
                    == HttpStatus.FORBIDDEN.value()) {

                terminateRefreshSession(
                        request,
                        response
                );
            }

            return buildFeignErrorResponse(
                    ex,
                    request
            );
        }
    }

    // =========================================================
    // 4. LOGOUT
    // =========================================================

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        /*
         * Invalid CSRF must NOT log the user out.
         */

        if (!isCsrfValid(request)) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .build();
        }

        String refreshToken =
                extractCookie(
                        request,
                        REFRESH_COOKIE
                );

        String csrfToken =
                request.getHeader(
                        CSRF_HEADER
                );

        /*
         * Logout is idempotent.
         *
         * No refresh token means there is simply
         * no Identity session to revoke.
         */

        if (refreshToken != null) {

            try {

                identityClient.logout(
                        REFRESH_COOKIE
                                + "="
                                + refreshToken
                );

            } catch (FeignException ignored) {

                /*
                 * Best effort logout.
                 *
                 * Even if Identity is unavailable,
                 * remove browser-side session state.
                 *
                 * The server-side refresh token may remain
                 * until its Redis TTL expires.
                 */
            }
        }

        /*
         * Delete Gateway CSRF state.
         */

        csrfTokenService.delete(
                csrfToken
        );

        /*
         * Delete browser cookies.
         */

        deleteCookie(
                response,
                REFRESH_COOKIE
        );

        deleteCookie(
                response,
                CSRF_COOKIE
        );

        /*
         * Frontend must discard access token as well.
         *
         * JWT is stateless and cannot be immediately revoked
         * unless token blacklisting is implemented.
         */

        return ResponseEntity
                .noContent()
                .build();
    }

    // =========================================================
    // 5. FEIGN ERROR RESPONSE
    // =========================================================

    private ResponseEntity<ErrorResponse>
    buildFeignErrorResponse(
            FeignException ex,
            HttpServletRequest request
    ) {

        int statusCode = ex.status();

        /*
         * Feign uses -1 when it could not obtain
         * a real HTTP response.
         *
         * Example:
         * - Identity service is down
         * - connection refused
         * - network failure
         */

        if (statusCode < 400 || statusCode >= 600) {

            return buildResponse(
                    HttpStatus.BAD_GATEWAY,
                    "IDENTITY_SERVICE_UNAVAILABLE",
                    "Authentication service is currently unavailable",
                    request
            );
        }

        String responseBody =
                ex.contentUTF8();

        /*
         * Identity normally returns our common ErrorResponse JSON.
         *
         * Deserialize it instead of forwarding a raw String.
         */

        if (responseBody != null
                && !responseBody.isBlank()) {

            try {

                ErrorResponse errorResponse =
                        objectMapper.readValue(
                                responseBody,
                                ErrorResponse.class
                        );

                return ResponseEntity
                        .status(
                                resolveStatus(statusCode)
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .body(errorResponse);

            } catch (IOException ignored) {

                /*
                 * Identity returned a response body,
                 * but it was not our expected ErrorResponse.
                 *
                 * Fall through to a safe Gateway response.
                 */
            }
        }

        /*
         * Identity returned an error without a usable
         * ErrorResponse body.
         */

        return buildResponse(
                resolveStatus(statusCode),
                "UPSTREAM_ERROR",
                "Authentication service returned an error",
                request
        );
    }

    // =========================================================
    // 6. BUILD ERROR RESPONSE
    // =========================================================

    private ResponseEntity<ErrorResponse>
    buildResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {

        ErrorResponse response =
                new ErrorResponse(
                        Instant.now(),
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
    // 7. CSRF
    // =========================================================

    private String ensureCsrfToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        String existingToken =
                extractCookie(
                        request,
                        CSRF_COOKIE
                );

        /*
         * Reuse existing valid token.
         */

        if (existingToken != null
                && csrfTokenService.validate(existingToken)) {

            return existingToken;
        }

        /*
         * Existing cookie is stale.
         */

        if (existingToken != null) {

            csrfTokenService.delete(
                    existingToken
            );
        }

        /*
         * Create new CSRF token.
         */

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

    private boolean isCsrfValid(
            HttpServletRequest request
    ) {

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

        /*
         * Double-submit check:
         *
         * header == cookie
         *
         * AND
         *
         * token exists in Redis.
         */

        return headerToken.equals(cookieToken)
                && csrfTokenService.validate(
                        headerToken
                );
    }

    // =========================================================
    // 8. TERMINATE REFRESH SESSION
    // =========================================================

    private void terminateRefreshSession(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

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

        /*
         * Try to revoke refresh token in Identity.
         */

        if (refreshToken != null) {

            try {

                identityClient.logout(
                        REFRESH_COOKIE
                                + "="
                                + refreshToken
                );

            } catch (FeignException ignored) {

                /*
                 * Best effort.
                 *
                 * Identity may be unavailable.
                 */
            }
        }

        /*
         * Remove Gateway-side CSRF state.
         */

        csrfTokenService.delete(
                csrfCookie
        );

        /*
         * Remove browser cookies.
         */

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
    // 9. COOKIE HELPERS
    // =========================================================

    private String extractCookie(
            HttpServletRequest request,
            String name
    ) {

        Cookie[] cookies =
                request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {

            if (name.equals(cookie.getName())) {

                return cookie.getValue();
            }
        }

        return null;
    }

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            boolean httpOnly,
            Duration maxAge
    ) {

        ResponseCookie cookie =
                ResponseCookie
                        .from(
                                name,
                                value
                        )
                        .httpOnly(httpOnly)
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

    private void deleteCookie(
            HttpServletResponse response,
            String name
    ) {

        ResponseCookie cookie =
                ResponseCookie
                        .from(
                                name,
                                ""
                        )
                        .httpOnly(
                                REFRESH_COOKIE.equals(name)
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

    // =========================================================
    // 10. STATUS HELPER
    // =========================================================

    private HttpStatus resolveStatus(
            int status
    ) {

        if (status >= 400 && status < 600) {

            try {

                return HttpStatus.valueOf(status);

            } catch (IllegalArgumentException ignored) {

                return HttpStatus.INTERNAL_SERVER_ERROR;
            }
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

