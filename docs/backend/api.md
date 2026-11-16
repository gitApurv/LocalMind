# HTTP API

Index of every endpoint. Request/response bodies, JSON examples, validation limits and error strings are on the
linked feature pages. All bodies are JSON unless noted; all errors are `ProblemDetail` ([errors.md](errors.md)).

**Auth column:** *Bearer* = `Authorization: Bearer <access token>` required; *Cookie* = `refreshToken` cookie;
*Public* = no credentials.

## Auth — [auth.md](features/auth.md#http-api)

| Method | Path | Auth | Request | Response | Status |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Public | `RegisterRequest` | `UserSummary` | 201 |
| `POST` | `/api/auth/login` | Public | `LoginRequest` | `AuthResponse` + `Set-Cookie: refreshToken` | 200 |
| `POST` | `/api/auth/refresh` | Cookie | — | `RefreshTokenResponse` + rotated cookie | 200 |
| `POST` | `/api/auth/logout` | Cookie | — | — (cookie expired) | 204 |
| `GET` | `/api/auth/me` | Bearer | — | `UserSummary` | 200 |
| `PATCH` | `/api/auth/me` | Bearer | `UpdateProfileRequest` | `UserSummary` | 200 |

## Conversations — [conversation.md](features/conversation.md#http-api)

| Method | Path | Auth | Request | Response | Status |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/conversations` | Bearer | `CreateConversationRequest` (optional) | `ConversationResponse` | 201 |
| `GET` | `/api/conversations` | Bearer | — | `ConversationResponse[]`, newest `updatedAt` first | 200 |
| `PATCH` | `/api/conversations/{id}` | Bearer | `UpdateConversationRequest` | `ConversationResponse` | 200 |
| `DELETE` | `/api/conversations/{id}` | Bearer | — | — | 204 |
| `GET` | `/api/conversations/{id}/messages` | Bearer | — | `MessageResponse[]`, oldest first | 200 |

## Chat — [chat.md](features/chat.md#http-api)

| Method | Path | Auth | Request | Response | Status |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/chat/stream` | Bearer | `ChatRequest` | `text/event-stream` of `ChatEvent` | 200 |

Event types: `CONTENT`, `REASONING`, `TOOL_START`, `TOOL_END`, `ERROR`, `DONE`. See
[chat.md — SSE wire format](features/chat.md#sse-wire-format) and [flows/chat-streaming.md](flows/chat-streaming.md).

## Attachments — [attachment.md](features/attachment.md#http-api)

| Method | Path | Auth | Request | Response | Status |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/conversations/{conversationId}/attachments` | Bearer | `multipart/form-data`, part `file` | `UploadResponse` | 202 |
| `GET` | `/api/conversations/{conversationId}/attachments` | Bearer | — | `AttachmentResponse[]`, newest first | 200 |
| `GET` | `/api/conversations/{conversationId}/attachments/{attachmentId}/download` | Bearer | — | File bytes, `Content-Disposition: attachment`, `Content-Type` from the extension, `X-Content-Type-Options: nosniff` | 200 |

Attachments are deleted with their conversation; there is no single-attachment delete.

## Memories — [memory.md](features/memory.md#http-api)

| Method | Path | Auth | Request | Response | Status |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/memories` | Bearer | — | `MemoryResponse[]`, newest first | 200 |
| `PATCH` | `/api/memories/{id}` | Bearer | `UpdateMemoryRequest` (`pinned`) | `MemoryResponse` | 200 |
| `DELETE` | `/api/memories/{id}` | Bearer | — | — | 204 |

Memories are created only by the model through the `saveUserMemory` tool.

## Models — [llm.md](features/llm.md#http-api)

| Method | Path | Auth | Request | Response | Status |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/models` | Bearer | — | `ModelResponse` (`models`, `defaultModel`) | 200 |

## Actuator

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `GET` | `/actuator/health` | Public | Details only for authenticated callers |
| `GET` | `/actuator/metrics` | Bearer | Exposed, not public |

## Common behaviour

- **Ownership.** Any `{id}`/`{conversationId}` that is not the caller's conversation returns 403, including ids that
  do not exist. A memory that is not the caller's returns 404.
- **Lists are not paginated.**
- **Validation** failures return 400 with `message` = `field: error; field: error`.
- **Request size.** Multipart uploads above `UPLOAD_MAX_FILE_SIZE` (25 MB) or `UPLOAD_MAX_REQUEST_SIZE` (30 MB) return
  413.
