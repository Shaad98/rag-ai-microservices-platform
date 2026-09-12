package com.shaadrag.document.exception;

public class DocumentLimitExceededException extends RuntimeException {

    public DocumentLimitExceededException(String message) {
        super(message);
    }
}