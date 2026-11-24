package com.localmind.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the caller has no valid session: wrong credentials, a missing, invalid or reused refresh token, or a
 * token for a user that no longer exists; rendered as {@code 401} by {@link GlobalExceptionHandler}.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }

}
