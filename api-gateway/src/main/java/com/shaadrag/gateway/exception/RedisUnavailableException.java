package com.shaadrag.gateway.exception;

public class RedisUnavailableException extends RuntimeException {

    public RedisUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}