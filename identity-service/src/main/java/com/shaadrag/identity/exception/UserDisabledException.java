package com.shaadrag.identity.exception;

public class UserDisabledException
        extends RuntimeException {

    public UserDisabledException(
            String message) {

        super(message);
    }
}