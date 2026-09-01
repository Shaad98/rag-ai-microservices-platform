package com.shaadrag.identity.model;

// import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenData {

    private String userId;

    // private LocalDateTime createdAt;

    // private boolean revoked;
}