package com.shaadrag.document.handler;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.shaadrag.document.dto.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j 
@RestControllerAdvice 
public class GlobalExceptionHandler {

      // =========================================================
    // INVALID JSON
    // =========================================================

    @ExceptionHandler(
            HttpMessageNotReadableException.class
    )
    public ResponseEntity<ErrorResponse>
    handleInvalidRequest(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_JSON",
                "Request body contains invalid JSON",
                request
        );
    }

    // =========================================================
    // REQUEST VALIDATION
    // =========================================================

    @ExceptionHandler(
            MethodArgumentNotValidException.class
    )
    public ResponseEntity<ErrorResponse>
    handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {

        String message =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error ->
                                error.getField()
                                        + ": "
                                        + error.getDefaultMessage()
                        )
                        .findFirst()
                        .orElse(
                                "Request validation failed"
                        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                request
        );
    }

    // =========================================================
    // CONSTRAINT VALIDATION
    // =========================================================

    @ExceptionHandler(
            ConstraintViolationException.class
    )
    public ResponseEntity<ErrorResponse>
    handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {

        String message =
                ex.getConstraintViolations()
                        .stream()
                        .map(violation ->
                                violation.getPropertyPath()
                                        + ": "
                                        + violation.getMessage()
                        )
                        .findFirst()
                        .orElse(
                                "Request validation failed"
                        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                request
        );
    }

    // =========================================================
    // METHOD NOT SUPPORTED
    // =========================================================

    @ExceptionHandler(
            HttpRequestMethodNotSupportedException.class
    )
    public ResponseEntity<ErrorResponse>
    handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {

        return buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "HTTP method is not supported for this endpoint",
                request
        );
    }

    // =========================================================
    // FALLBACK
    // =========================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse>
    handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {

        log.error(
                "Unexpected Gateway exception",
                ex
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request
        );
    }

    // =========================================================
    // BUILD RESPONSE
    // =========================================================

    private ResponseEntity<ErrorResponse>
    buildResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {

        ErrorResponse response =
                new ErrorResponse(
                        // Instant.now(),
                        LocalDateTime.now(),
                        status.value(),
                        code,
                        message,
                        request.getRequestURI()
                );

        return ResponseEntity
                .status(status)
                .body(response);
    }
}
