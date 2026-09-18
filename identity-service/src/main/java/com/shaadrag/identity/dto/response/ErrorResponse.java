package com.shaadrag.identity.dto.response;

import java.time.*;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path
) {}