package com.localmind.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request breaks a business rule that Bean Validation cannot express; rendered as {@code 400} by
 * {@link GlobalExceptionHandler}.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

}
