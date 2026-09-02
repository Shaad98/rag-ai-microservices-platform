package com.shaadrag.gateway.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.shaadrag.gateway.dto.request.LoginRequest;
import com.shaadrag.gateway.dto.response.AuthResponse;
import com.shaadrag.gateway.dto.response.LoginResponse;

@FeignClient(name = "${IDENTITY_SERVICE}")
public interface IdentityClient {

    @PostMapping("/auth/login")
    ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request
    );

    @PostMapping("/auth/refresh")
    ResponseEntity<AuthResponse> refresh(
            @RequestHeader("Cookie") String refreshTokenCookie
    );

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(
            @RequestHeader("Cookie") String refreshTokenCookie
    );
}