package com.localmind.common.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds the RFC 9457 error body used for every error response. The {@code message} property mirrors {@code detail}
 * because the frontend reads {@code message}.
 */
public final class ProblemDetails {

    public static final String ACCESS_DENIED_MESSAGE = "You do not have access to this resource";

    private static final String MESSAGE_PROPERTY = "message";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setProperty(MESSAGE_PROPERTY, message);
        return problem;
    }

    public static ProblemDetail withMessage(ProblemDetail problem) {
        String message = problem.getDetail() != null ? problem.getDetail() : problem.getTitle();
        problem.setProperty(MESSAGE_PROPERTY, message);
        return problem;
    }
}
