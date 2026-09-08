package com.shaadrag.identity.controller;

import com.shaadrag.identity.dto.request.LoginRequest;
import com.shaadrag.identity.dto.request.RegisterRequest;
import com.shaadrag.identity.dto.response.LoginResponse;
import com.shaadrag.identity.dto.response.RefreshTokenResponse;
import com.shaadrag.identity.dto.response.RegisterResponse;
import com.shaadrag.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // =========================================================
    // REGISTER
    // =========================================================

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @RequestBody RegisterRequest request) {

        RegisterResponse response =
                authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =========================================================
    // LOGIN
    // =========================================================

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request) {

        LoginResponse response =
                authService.login(request);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // REFRESH
    // =========================================================

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(
            @RequestHeader("Cookie") String refreshTokenCookie) {

        RefreshTokenResponse response =
                authService.refresh(
                        refreshTokenCookie
                );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // LOGOUT
    // =========================================================

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(
                    value = "Cookie",
                    required = false
            )
            String refreshTokenCookie) {

        authService.logout(
                refreshTokenCookie
        );

        /*
         * Logout is idempotent.
         *
         * Missing refresh token is still a successful logout.
         */
        return ResponseEntity.noContent().build();
    }
}