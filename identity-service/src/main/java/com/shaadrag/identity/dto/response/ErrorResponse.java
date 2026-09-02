package com.shaadrag.identity.dto.response;

// import lombok.AllArgsConstructor;
// import lombok.Data;
// import lombok.NoArgsConstructor;

// @Data
// @AllArgsConstructor
// @NoArgsConstructor

// public class ErrorResponse {

//     private String exception;
//     private String message;
//     private int status;
// }

import java.time.*;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path
) {}