package com.localmind.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base of the business exceptions that map to a specific HTTP status: {@link BadRequestException} (400),
 * {@link UnauthorizedException} (401), {@link ResourceNotFoundException} (404) and {@link ConflictException} (409).
 * Rendered by {@link GlobalExceptionHandler}, which sends the exception message to the client, so messages must be
 * fixed, user-facing text.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    protected ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
