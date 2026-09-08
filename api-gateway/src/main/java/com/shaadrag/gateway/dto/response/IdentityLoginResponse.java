package com.shaadrag.gateway.dto.response;

public record IdentityLoginResponse(
        String accessToken,
        String refreshToken
) {
}