package com.localmind.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for business errors that map to a specific HTTP status. Rendered by {@link GlobalExceptionHandler}.
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
