package com.shaadrag.identity.handler;

import com.shaadrag.identity.dto.response.ErrorResponse;
import com.shaadrag.identity.exception.EmailVerificationRequiredException;
import com.shaadrag.identity.exception.InvalidRefreshTokenException;
import com.shaadrag.identity.exception.UserAlreadyExistsException;
import com.shaadrag.identity.exception.UserDisabledException;
import com.shaadrag.identity.exception.UserNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.http.converter.HttpMessageNotReadableException;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // =========================================================
    // INVALID JSON
    // =========================================================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_JSON",
                "Request body contains invalid JSON",
                request
        );
    }

    // =========================================================
    // REQUEST VALIDATION
    // =========================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {

        String message = ex.getBindingResult()
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

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                request
        );
    }

    // =========================================================
    // CONSTRAINT VALIDATION
    // =========================================================

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {

        String message = ex.getConstraintViolations()
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

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                request
        );
    }

    // =========================================================
    // INVALID CREDENTIALS
    // =========================================================

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request
    ) {

        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password",
                request
        );
    }

    // =========================================================
    // DISABLED USER
    // =========================================================

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledUser(
            DisabledException ex,
            HttpServletRequest request
    ) {

        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "ACCOUNT_NOT_VERIFIED",
                "Please verify your email before logging in",
                request
        );
    }

    // =========================================================
    // EMAIL VERIFICATION REQUIRED
    // =========================================================

    @ExceptionHandler(EmailVerificationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleEmailVerificationRequired(
            EmailVerificationRequiredException ex,
            HttpServletRequest request
    ) {

        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "EMAIL_VERIFICATION_REQUIRED",
                ex.getMessage(),
                request
        );
    }

    // =========================================================
    // USER NOT FOUND
    // =========================================================

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex,
            HttpServletRequest request
    ) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                ex.getMessage(),
                request
        );
    }

    // =========================================================
    // USER ALREADY EXISTS
    // =========================================================

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex,
            HttpServletRequest request
    ) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "USER_ALREADY_EXISTS",
                ex.getMessage(),
                request
        );
    }

    // =========================================================
    // INVALID REFRESH TOKEN
    // =========================================================

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex,
            HttpServletRequest request
    ) {

        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN",
                "Your session has expired. Please log in again.",
                request
        );
    }

    // =========================================================
    // USER DISABLED AFTER LOGIN
    // =========================================================

    @ExceptionHandler(UserDisabledException.class)
    public ResponseEntity<ErrorResponse> handleUserDisabled(
            UserDisabledException ex,
            HttpServletRequest request
    ) {

        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "ACCOUNT_DISABLED",
                "Your account is disabled.",
                request
        );
    }

    // =========================================================
    // REDIS CONNECTION FAILURE
    // =========================================================

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ErrorResponse> handleRedisConnectionFailure(
            RedisConnectionFailureException ex,
            HttpServletRequest request
    ) {

        log.error(
                "Redis connection failure",
                ex
        );

        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "REDIS_UNAVAILABLE",
                "Redis service is temporarily unavailable",
                request
        );
    }

    // =========================================================
    // REDIS SYSTEM ERROR
    // =========================================================

    @ExceptionHandler(RedisSystemException.class)
    public ResponseEntity<ErrorResponse> handleRedisSystemError(
            RedisSystemException ex,
            HttpServletRequest request
    ) {

        log.error(
                "Redis system error",
                ex
        );

        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "REDIS_ERROR",
                "A Redis service error occurred",
                request
        );
    }

    // =========================================================
    // DATABASE / OTHER DATA ACCESS ERROR
    // =========================================================

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseError(
            DataAccessException ex,
            HttpServletRequest request
    ) {

        log.error(
                "Database error",
                ex
        );

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "DATABASE_ERROR",
                "A database error occurred",
                request
        );
    }

    // =========================================================
    // FALLBACK
    // =========================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {

        log.error(
                "Unexpected error",
                ex
        );

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request
        );
    }

    // =========================================================
    // BUILD ERROR RESPONSE
    // =========================================================

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {

        ErrorResponse response =
                new ErrorResponse(
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