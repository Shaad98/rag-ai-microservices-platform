package com.shaadrag.gateway.client;

import com.shaadrag.gateway.dto.AuthResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "${IDENTITY_SERVICE}")
public interface IdentityClient {

    @PostMapping("/auth/refresh")
    ResponseEntity<AuthResponse> refresh(
            @RequestHeader("Cookie") String refreshTokenCookie
    );

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(
            @RequestHeader("Cookie") String refreshTokenCookie
    );
}