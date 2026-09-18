package com.shaadrag.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaadrag.gateway.dto.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
// import java.time.Instant;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {

        ErrorResponse errorResponse =
                new ErrorResponse(
                        // Instant.now(),
                        LocalDateTime.now(),
                        status,
                        code,
                        message,
                        request.getRequestURI()
                );

        response.setStatus(status);

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        objectMapper.writeValue(
                response.getOutputStream(),
                errorResponse
        );
    }
}