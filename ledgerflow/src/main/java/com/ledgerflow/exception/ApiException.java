package com.ledgerflow.exception;

import org.springframework.http.HttpStatus;

/** A domain error that maps cleanly onto an HTTP status. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
