package com.shaadrag.document.dto.response;


// import java.time.LocalDateTime;

// import lombok.AllArgsConstructor;
// import lombok.Data;
// import lombok.NoArgsConstructor;

// @Data
// @AllArgsConstructor
// @NoArgsConstructor
// public class ErrorResponse {

//     private String message;

//     private LocalDateTime timestamp;
// }

import java.time.*;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path
) {}