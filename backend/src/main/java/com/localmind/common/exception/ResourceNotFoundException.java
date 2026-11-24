package com.localmind.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource does not exist; rendered as {@code 404} by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(HttpStatus.NOT_FOUND, message, cause);
    }
}
