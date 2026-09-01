package com.shaadrag.identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    
    // private String tokenType;
    // private long expiresIn;
    // private UserResponse user;

    // private UserReqResponse user;

}