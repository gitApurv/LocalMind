package com.localmind.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.localmind.common.exception.ProblemDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link ProblemDetailSecurityHandler}: 403 and 401 ProblemDetails written by the security filter chain
 * with the shared messages and {@code WWW-Authenticate} header.
 */
class ProblemDetailSecurityHandlerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
    private final ProblemDetailSecurityHandler handler = new ProblemDetailSecurityHandler(objectMapper);

    @Test
    @DisplayName("Access denied in the filter chain should be a 403 ProblemDetail with the shared message")
    void accessDeniedIsForbiddenProblem() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("missing scope"));

        assertEquals(403, response.getStatus());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON_VALUE, response.getContentType());
        assertNotNull(response.getHeader("WWW-Authenticate"));
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(403, body.get("status").asInt());
        assertEquals(ProblemDetails.ACCESS_DENIED_MESSAGE, body.get("message").asString());
    }

    @Test
    @DisplayName("Missing authentication in the filter chain should be a 401 ProblemDetail with WWW-Authenticate")
    void missingAuthenticationIsUnauthorizedProblem() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("no token"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").startsWith("Bearer"));
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("Authentication is required to access this resource", body.get("message").asString());
    }
}
