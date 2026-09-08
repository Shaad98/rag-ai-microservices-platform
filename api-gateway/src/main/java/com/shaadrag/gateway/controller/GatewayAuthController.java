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

        String csrfToken = ensureCsrfToken(request, response);

        return ResponseEntity.ok(
                Map.of("token", csrfToken)
        );
    }

    // =========================================================
    // 2. LOGIN
    // =========================================================

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        /*
         * Login does not strictly need CSRF protection when credentials
         * are sent explicitly, but we still establish the CSRF session
         * here so the browser is ready for refresh/logout.
         */
        ensureCsrfToken(httpRequest, httpResponse);

        try {

            ResponseEntity<IdentityLoginResponse>
                    identityResponse =
                    identityClient.login(request);

            if (!identityResponse.getStatusCode().is2xxSuccessful()
                    || identityResponse.getBody() == null) {

                return ResponseEntity
                        .status(identityResponse.getStatusCode())
                        .build();
            }

            var body = identityResponse.getBody();

            /*
             * IMPORTANT:
             *
             * Refresh token is NEVER returned in the JSON response.
             * It goes only into the HttpOnly cookie.
             */
            addCookie(
                    httpResponse,
                    REFRESH_COOKIE,
                    body.refreshToken(),
                    true,
                    REFRESH_TTL
            );

            return ResponseEntity.ok(
                    new LoginResponse(body.accessToken())
            );

        } catch (FeignException ex) {

            return ResponseEntity
                    .status(resolveStatus(ex.status()))
                    .build();
        }
    }

    // =========================================================
    // 3. REFRESH
    // =========================================================

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        /*
         * CSRF is mandatory for refresh because the browser
         * automatically sends the HttpOnly refresh cookie.
         */
        if (!isCsrfValid(request)) {

            terminateRefreshSession(request, response);

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .build();
        }

        String refreshToken =
                extractCookie(request, REFRESH_COOKIE);

        /*
         * No refresh token means there is no session to refresh.
         */
        if (refreshToken == null) {

            terminateRefreshSession(request, response);

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
             * Identity should return 200 with a new access token.
             */
            if (identityResponse.getStatusCode().is2xxSuccessful()) {

                return identityResponse;
            }

            /*
             * Invalid refresh / disabled account:
             * destroy browser session.
             */
            if (identityResponse.getStatusCode() == HttpStatus.UNAUTHORIZED
                    || identityResponse.getStatusCode() == HttpStatus.FORBIDDEN) {

                terminateRefreshSession(request, response);
            }

            return identityResponse;

        } catch (FeignException ex) {

            /*
             * Identity tells us the refresh session is invalid.
             */
            if (ex.status() == 401 || ex.status() == 403) {

                terminateRefreshSession(request, response);
            }

            return ResponseEntity
                    .status(resolveStatus(ex.status()))
                    .build();
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
         * This protects logout against cross-site logout attempts.
         */
        if (!isCsrfValid(request)) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .build();
        }

        String refreshToken =
                extractCookie(request, REFRESH_COOKIE);

        String csrfToken =
                request.getHeader(CSRF_HEADER);

        /*
         * Logout should be idempotent.
         *
         * Missing refresh token is not a logout failure.
         */
        if (refreshToken != null) {

            try {

                @SuppressWarnings("unused")
                ResponseEntity<Void> identityResponse =
                        identityClient.logout(
                                REFRESH_COOKIE + "=" + refreshToken
                        );

                /*
                 * Even if Identity says the refresh token is already
                 * invalid/expired, local browser state should still be
                 * cleaned up.
                 */

            } catch (FeignException ex) {

                /*
                 * Even if Identity is unavailable, remove local cookies.
                 *
                 * IMPORTANT:
                 * the server-side refresh token may remain until Redis TTL
                 * expires if Identity is unavailable.
                 */
            }
        }

        /*
         * Delete Gateway CSRF state.
         */
        csrfTokenService.delete(csrfToken);

        /*
         * Delete browser session cookies.
         */
        deleteCookie(response, REFRESH_COOKIE);
        deleteCookie(response, CSRF_COOKIE);

        /*
         * The frontend must also discard its access token.
         *
         * Since JWT is stateless, Gateway cannot revoke that access token
         * immediately unless you implement token blacklisting.
         */
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // CSRF
    // =========================================================

    private String ensureCsrfToken(
            HttpServletRequest request,
            HttpServletResponse response) {

        String existingToken =
                extractCookie(request, CSRF_COOKIE);

        /*
         * Cookie exists AND Redis entry exists.
         *
         * Reuse it.
         */
        if (existingToken != null
                && csrfTokenService.validate(existingToken)) {

            return existingToken;
        }

        /*
         * If a stale cookie exists but Redis no longer has the token,
         * remove its Redis entry and issue a new token.
         */
        if (existingToken != null) {
            csrfTokenService.delete(existingToken);
        }

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
                request.getHeader(CSRF_HEADER);

        String cookieToken =
                extractCookie(request, CSRF_COOKIE);

        if (headerToken == null
                || cookieToken == null) {

            return false;
        }

        /*
         * Double-submit check:
         *
         * header == cookie
         *
         * plus
         *
         * token exists in Redis.
         */
        return headerToken.equals(cookieToken)
                && csrfTokenService.validate(headerToken);
    }

    // =========================================================
    // TERMINATE REFRESH SESSION
    // =========================================================

    private void terminateRefreshSession(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken =
                extractCookie(request, REFRESH_COOKIE);

        String csrfCookie =
                extractCookie(request, CSRF_COOKIE);

        /*
         * Revoke refresh token from Identity if possible.
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
                 * If Identity is down, browser-side session state
                 * is still cleared.
                 */
            }
        }

        /*
         * Delete CSRF server-side state.
         */
        csrfTokenService.delete(csrfCookie);

        /*
         * Delete browser cookies.
         */
        deleteCookie(response, REFRESH_COOKIE);
        deleteCookie(response, CSRF_COOKIE);
    }

    // =========================================================
    // COOKIE HELPERS
    // =========================================================

    private String extractCookie(
            HttpServletRequest request,
            String name) {

        Cookie[] cookies = request.getCookies();

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
                ResponseCookie.from(name, value)
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
                ResponseCookie.from(name, "")
                        .httpOnly("refresh_token".equals(name))
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

    private HttpStatus resolveStatus(int status) {

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