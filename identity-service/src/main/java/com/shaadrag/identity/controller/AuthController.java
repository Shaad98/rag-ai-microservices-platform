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
    // 1. REGISTER
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
    // 2. LOGIN
    // =========================================================

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request) {

        LoginResponse response =
                authService.login(request);

        return ResponseEntity.ok(response);
    }


    // =========================================================
    // 3. REFRESH
    // =========================================================

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(
            @RequestHeader("Cookie") String refreshTokenCookie) {

        RefreshTokenResponse response =
                authService.refresh(refreshTokenCookie);

        return ResponseEntity.ok(response);
    }


    // =========================================================
    // 4. LOGOUT
    // =========================================================

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Cookie") String refreshTokenCookie) {

        authService.logout(refreshTokenCookie);

        return ResponseEntity.noContent().build();
    }
}