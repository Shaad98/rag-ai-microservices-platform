package com.shaadrag.gateway.exception;

import com.shaadrag.gateway.dto.response.ErrorResponse;
import lombok.Getter;

@Getter
public class IdentityServiceException extends RuntimeException {

    private final int status;
    private final ErrorResponse errorResponse;

    public IdentityServiceException(
            int status,
            ErrorResponse errorResponse) {

        super(
                errorResponse != null
                        ? errorResponse.message()
                        : "Authentication service returned an error"
        );

        this.status = status;
        this.errorResponse = errorResponse;
    }
}