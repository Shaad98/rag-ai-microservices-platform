package com.shaadrag.gateway.dto.response;

// import lombok.AllArgsConstructor;
// import lombok.Data;
// import lombok.NoArgsConstructor;

// @Data
// @AllArgsConstructor
// @NoArgsConstructor
// public class LoginResponse {
//     private String accessToken;
//     private String refreshToken;
// }



public record LoginResponse(
        String accessToken,
        String refreshToken
) {}