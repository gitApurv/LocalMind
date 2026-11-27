# Errors

Every error response from the backend is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `ProblemDetail` with
`Content-Type: application/problem+json` and an extra `message` property, which the frontend displays.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Email is already registered",
  "instance": "/api/auth/register",
  "message": "Email is already registered"
}
```

`message` always equals `detail`, or `title` when Spring left `detail` empty.

## Where errors are rendered

| Source | Renderer | Notes |
| --- | --- | --- |
| `ApiException` subclasses thrown by services | [`GlobalExceptionHandler`](../../backend/src/main/java/com/localmind/common/exception/GlobalExceptionHandler.java) | Status and message come from the exception; 5xx are logged at `ERROR` |
| `AccessDeniedException` (ownership checks) | `GlobalExceptionHandler` | Always 403 `You do not have access to this resource` |
| `DataIntegrityViolationException` | `GlobalExceptionHandler` | 409 `The request conflicts with existing data`; cause logged at `WARN` |
| `MethodArgumentNotValidException` (`@Valid`) | `GlobalExceptionHandler` | 400; `message` is `field: error; field: error`, or `Validation failed` |
| Standard Spring MVC exceptions | `ResponseEntityExceptionHandler` base class | Malformed JSON, missing parameter, unsupported media type, upload too large, unknown route, ... `message` added by `handleExceptionInternal` |
| Missing or invalid token in the filter chain | [`ProblemDetailSecurityHandler`](../../backend/src/main/java/com/localmind/common/security/ProblemDetailSecurityHandler.java) | 401 `Authentication is required to access this resource`; keeps the RFC 6750 `WWW-Authenticate` header |
| Access denied in the filter chain | `ProblemDetailSecurityHandler` | 403 `You do not have access to this resource` |
| Anything else | `GlobalExceptionHandler#handleUnexpected` | 500 `An unexpected error occurred. Please try again.`; full exception logged |
| Errors during a chat stream | `ChatTurn` | Sent as an SSE `ERROR` event, not a `ProblemDetail`; see [chat streaming](flows/chat-streaming.md) |
| Invalid configuration at startup | `@ConfigurationProperties` compact constructors | Not an HTTP error: the application fails to start, e.g. `localmind.storage.allowed-extensions` with a value outside `pdf, txt, docx, md` throws `IllegalArgumentException` |

## Exception types

| Exception | Status | Use for |
| --- | --- | --- |
| `BadRequestException` | 400 | Invalid input that Bean Validation cannot express (file type, empty file, ...) |
| `UnauthorizedException` | 401 | Bad credentials, invalid or expired refresh token, no authenticated user, token for a user that no longer exists (`User not found`) |
| `AccessDeniedException` (Spring Security) | 403 | The caller does not own the resource |
| `ResourceNotFoundException` | 404 | The resource does not exist |
| `ConflictException` | 409 | Unique constraint at the business level (e.g. email already registered) |

Each feature page lists the exact situations and messages it produces:
[auth](features/auth.md#errors), [conversation](features/conversation.md#errors), [chat](features/chat.md#errors),
[attachment](features/attachment.md#errors), [memory](features/memory.md#errors), [llm](features/llm.md#errors).

## Rules

- Messages are fixed, user-facing strings. Exception text (`e.getMessage()`) never reaches a client, an SSE `ERROR`
  event, a stored status message or a tool result.
- Controllers never catch exceptions or build error responses.
- LLM tools and prompt advisors never throw into the model pipeline: tools return a readable error string and advisors
  return the unmodified request. See [tools](features/tools.md).
