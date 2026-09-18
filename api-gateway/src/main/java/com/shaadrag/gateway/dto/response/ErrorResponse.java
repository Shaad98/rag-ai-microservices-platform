package com.shaadrag.gateway.dto.response;

// import java.time.Instant;
import java.time.LocalDateTime;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path
) {
}