package com.shaadrag.gateway.controller;

import com.shaadrag.gateway.client.IdentityClient;
import com.shaadrag.gateway.dto.request.LoginRequest;
import com.shaadrag.gateway.dto.response.IdentityLoginResponse;
import com.shaadrag.gateway.dto.response.LoginResponse;
import com.shaadrag.gateway.dto.response.RefreshTokenResponse;
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
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class GatewayAuthController {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String REFRESH_COOKIE = "refresh_token";

    private static final Duration CSRF_TTL = Duration.ofDays(3);
    private static final Duration REFRESH_TTL = Duration.ofDays(1);

    private final CsrfTokenService csrfTokenService;
    private final IdentityClient identityClient;

    // =========================================================
    // 1. GET CSRF
    // =========================================================

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(
            HttpServletRequest request,
            HttpServletResponse response) {

        String csrfToken = ensureCsrfToken(
                request,
                response
        );

        return ResponseEntity.ok(
                Map.of("token", csrfToken)
        );
    }

    // =========================================================
    // 2. LOGIN
    // =========================================================

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        /*
         * Login does not strictly need CSRF protection because
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

            ResponseEntity<IdentityLoginResponse> identityResponse =
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
             * This normally will not be reached for Feign errors,
             * because Feign throws FeignException for non-2xx.
             */
            return ResponseEntity
                    .status(identityResponse.getStatusCode())
                    .body(identityResponse.getBody());

        } catch (FeignException ex) {

            /*
             * Identity already converted its exception into a
             * structured JSON ErrorResponse.
             *
             * FeignException contains that HTTP response body.
             *
             * We preserve it and send it to frontend.
             */
            return buildFeignErrorResponse(ex);
        }
    }

    // =========================================================
    // 3. REFRESH
    // =========================================================

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        /*
         * Refresh is CSRF protected because the browser
         * automatically sends the HttpOnly refresh cookie.
         */
        if (!isCsrfValid(request)) {

            terminateRefreshSession(
                    request,
                    response
            );

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .build();
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

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        try {

            ResponseEntity<RefreshTokenResponse> identityResponse =
                    identityClient.refresh(
                            REFRESH_COOKIE + "=" + refreshToken
                    );

            /*
             * Successful refresh.
             */
            if (identityResponse.getStatusCode().is2xxSuccessful()) {

                return ResponseEntity
                        .status(identityResponse.getStatusCode())
                        .body(identityResponse.getBody());
            }

            /*
             * Normally Feign throws before reaching here.
             *
             * But if a non-exception response is ever returned,
             * preserve its status/body.
             */
            if (identityResponse.getStatusCode() == HttpStatus.UNAUTHORIZED
                    || identityResponse.getStatusCode() == HttpStatus.FORBIDDEN) {

                terminateRefreshSession(
                        request,
                        response
                );
            }

            return ResponseEntity
                    .status(identityResponse.getStatusCode())
                    .body(identityResponse.getBody());

        } catch (FeignException ex) {

            /*
             * Identity returned an authentication/session error.
             *
             * 401 / 403 means:
             * - refresh session is no longer usable
             * - remove browser refresh cookie
             * - remove CSRF session state
             */
            if (ex.status() == HttpStatus.UNAUTHORIZED.value()
                    || ex.status() == HttpStatus.FORBIDDEN.value()) {

                terminateRefreshSession(
                        request,
                        response
                );
            }

            /*
             * IMPORTANT:
             *
             * Return Identity's original ErrorResponse JSON
             * instead of returning an empty response.
             */
            return buildFeignErrorResponse(ex);
        }
    }

    // =========================================================
    // 4. LOGOUT
    // =========================================================

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        /*
         * Invalid CSRF must NOT log the user out.
         *
         * Otherwise another website could force a logout.
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
                request.getHeader(CSRF_HEADER);

        /*
         * Logout is idempotent.
         *
         * No refresh token means there is simply
         * no Identity session to revoke.
         */
        if (refreshToken != null) {

            try {

                identityClient.logout(
                        REFRESH_COOKIE + "=" + refreshToken
                );

            } catch (FeignException ex) {

                /*
                 * Best effort logout.
                 *
                 * Even if Identity is unavailable, we still
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
         * unless you implement token blacklisting.
         */
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // 5. FEIGN ERROR RESPONSE
    // =========================================================

    private ResponseEntity<?> buildFeignErrorResponse(
            FeignException ex) {

        int statusCode = ex.status();

        /*
         * status() can be -1 when Feign cannot obtain
         * an HTTP response, for example connection failure
         * or Identity service being unavailable.
         */
        if (statusCode < 400 || statusCode >= 600) {

            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                          {
                              "status": 502,
                              "code": "IDENTITY_SERVICE_UNAVAILABLE",
                              "message": "Authentication service is currently unavailable"
                          }
                          """);
        }

        String responseBody = ex.contentUTF8();

        /*
         * Identity normally returns JSON from its
         * GlobalExceptionHandler.
         */
        if (responseBody != null
                && !responseBody.isBlank()) {

            return ResponseEntity
                    .status(
                            resolveStatus(statusCode)
                    )
                    .contentType(
                            MediaType.APPLICATION_JSON
                    )
                    .body(responseBody);
        }

        /*
         * If Identity returned no body, still return
         * the correct HTTP status.
         */
        return ResponseEntity
                .status(
                        resolveStatus(statusCode)
                )
                .build();
    }

    // =========================================================
    // 6. CSRF
    // =========================================================

    private String ensureCsrfToken(
            HttpServletRequest request,
            HttpServletResponse response) {

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
    // 7. TERMINATE REFRESH SESSION
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

        /*
         * Try to revoke refresh token in Identity.
         */
        if (refreshToken != null) {

            try {

                identityClient.logout(
                        REFRESH_COOKIE + "=" + refreshToken
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
    // 8. COOKIE HELPERS
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
            Duration maxAge) {

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
            String name) {

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
    // 9. STATUS HELPER
    // =========================================================

    private HttpStatus resolveStatus(
            int status) {

        if (status >= 400 && status < 600) {

            try {

                return HttpStatus.valueOf(
                        status
                );

            } catch (IllegalArgumentException ignored) {

                return HttpStatus.INTERNAL_SERVER_ERROR;
            }
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}