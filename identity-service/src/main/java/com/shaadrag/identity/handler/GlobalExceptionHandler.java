package com.shaadrag.identity.handler;

import java.time.LocalDateTime;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.shaadrag.identity.dto.response.ErrorResponse;
import com.shaadrag.identity.exception.EmailSendingException;
import com.shaadrag.identity.exception.EmailVerificationRequiredException;
import com.shaadrag.identity.exception.UserAlreadyExistsException;
import com.shaadrag.identity.exception.UserNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password",
                request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledUser(
            DisabledException ex,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "ACCOUNT_NOT_VERIFIED",
                "Please verify your email before logging in",
                request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "USER_ALREADY_EXISTS",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(EmailSendingException.class)
    public ResponseEntity<ErrorResponse> handleEmailSending(
            EmailSendingException ex,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "EMAIL_SENDING_FAILED",
                "Unable to send verification email",
                request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseError(
            DataAccessException ex,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "DATABASE_ERROR",
                "A database error occurred",
                request);
    }

    @ExceptionHandler(EmailVerificationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleEmailVerificationRequired(
            EmailVerificationRequiredException ex,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "EMAIL_VERIFICATION_REQUIRED",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                code,
                message,
                request.getRequestURI());

        return ResponseEntity
                .status(status)
                .body(response);
    }

}