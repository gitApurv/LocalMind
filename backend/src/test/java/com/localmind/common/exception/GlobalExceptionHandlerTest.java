package com.localmind.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Unit tests for {@link GlobalExceptionHandler}: joined validation messages, ApiException status and message, 403
 * access denied, 409 data conflicts and generic 500s that never leak exception text.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Validation errors should be joined as 'field: message' pairs separated by '; '")
    void validationMessagesAreJoined() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must be a well-formed email address"));
        bindingResult.addError(new FieldError("request", "password", "must not be blank"));

        ProblemDetail problem = validationProblem(bindingResult);

        assertEquals(400, problem.getStatus());
        assertEquals("email: must be a well-formed email address; password: must not be blank", message(problem));
        assertEquals(problem.getDetail(), message(problem));
    }

    @Test
    @DisplayName("Validation errors without field errors should fall back to 'Validation failed'")
    void validationWithoutFieldErrors() throws NoSuchMethodException {
        ProblemDetail problem = validationProblem(new BeanPropertyBindingResult(new Object(), "request"));

        assertEquals("Validation failed", message(problem));
    }

    @Test
    @DisplayName("An ApiException should keep its status and message")
    void apiExceptionKeepsStatusAndMessage() {
        ProblemDetail problem = handler.handleApiException(new ConflictException("Email is already registered"));

        assertEquals(409, problem.getStatus());
        assertEquals("Email is already registered", message(problem));
    }

    @Test
    @DisplayName("AccessDeniedException should be a 403 with the shared access-denied message")
    void accessDeniedIsForbidden() {
        ProblemDetail problem = handler.handleAccessDenied(new AccessDeniedException("owner mismatch for c-1"));

        assertEquals(403, problem.getStatus());
        assertEquals(ProblemDetails.ACCESS_DENIED_MESSAGE, message(problem));
    }

    @Test
    @DisplayName("DataIntegrityViolationException should be a 409 without database details")
    void dataIntegrityViolationIsConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement",
                new SQLException("Duplicate entry 'secret@localmind.ai' for key 'users.email'"));

        ProblemDetail problem = handler.handleDataIntegrityViolation(ex);

        assertEquals(409, problem.getStatus());
        assertEquals("The request conflicts with existing data", message(problem));
    }

    @Test
    @DisplayName("An unexpected exception should be a 500 with a generic message and no exception text")
    void unexpectedExceptionIsGeneric500() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("db password is hunter2"));

        assertEquals(500, problem.getStatus());
        assertEquals("An unexpected error occurred. Please try again.", message(problem));
        assertFalse(problem.toString().contains("hunter2"));
    }

    private ProblemDetail validationProblem(BeanPropertyBindingResult bindingResult) throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("validationTarget", Object.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(ex, new HttpHeaders(),
                HttpStatus.BAD_REQUEST, null);

        return assertInstanceOf(ProblemDetail.class, response.getBody());
    }

    private static String message(ProblemDetail problem) {
        return (String) problem.getProperties().get("message");
    }

    @SuppressWarnings("unused")
    private static void validationTarget(Object request) {
        throw new UnsupportedOperationException("Only its signature is used");
    }
}
