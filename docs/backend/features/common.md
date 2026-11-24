# Common (`com.localmind.common`)

Shared infrastructure that every feature uses: security, error rendering, transaction-bound side effects, vector-store
conventions and HTTP client configuration. `common` depends on no feature and contains no business logic.

## Responsibilities

- Authenticate every request with a self-issued RS256 JWT and expose the caller's id via `@CurrentUserId`.
- Render every error, including those raised by the security filter chain, as an RFC 9457 `ProblemDetail`.
- Tie file, vector-store and async side effects to the outcome of the current database transaction.
- Define the Qdrant payload keys, document types and per-user filters every vector search must use.
- Provide the `shortTimeoutRestClient` for LocalMind's own short HTTP calls.

## Package layout

| Class | Sub-package | Role |
| --- | --- | --- |
| [`ChatContextKeys`](../../../backend/src/main/java/com/localmind/common/ai/ChatContextKeys.java) | `ai` | Keys shared by the orchestrator, advisors (advisor context) and tools (tool context) |
| [`VectorMetadata`](../../../backend/src/main/java/com/localmind/common/ai/VectorMetadata.java) | `ai` | Qdrant payload keys and document types |
| [`VectorFilters`](../../../backend/src/main/java/com/localmind/common/ai/VectorFilters.java) | `ai` | Multi-tenant filter expressions for vector searches |
| [`QdrantConfig`](../../../backend/src/main/java/com/localmind/common/config/QdrantConfig.java) | `config` | Creates keyword payload indexes at startup |
| [`QdrantProperties`](../../../backend/src/main/java/com/localmind/common/config/QdrantProperties.java) | `config` | `localmind.qdrant` |
| [`WebMvcConfig`](../../../backend/src/main/java/com/localmind/common/config/WebMvcConfig.java) | `config` | Async request timeout, `@CurrentUserId` resolver, `shortTimeoutRestClient` bean |
| [`ApiException`](../../../backend/src/main/java/com/localmind/common/exception/ApiException.java) and subclasses | `exception` | Business errors carrying an HTTP status |
| [`GlobalExceptionHandler`](../../../backend/src/main/java/com/localmind/common/exception/GlobalExceptionHandler.java) | `exception` | Maps exceptions to `ProblemDetail` responses |
| [`ProblemDetails`](../../../backend/src/main/java/com/localmind/common/exception/ProblemDetails.java) | `exception` | Builds `ProblemDetail` with the extra `message` property |
| [`SecurityConfig`](../../../backend/src/main/java/com/localmind/common/security/SecurityConfig.java) | `security` | Filter chain, CORS, BCrypt, RSA key, JWT encoder/decoder |
| [`JwtSigningKeyStore`](../../../backend/src/main/java/com/localmind/common/security/JwtSigningKeyStore.java) | `security` | Loads or generates the PKCS#8 PEM signing key |
| [`ProblemDetailSecurityHandler`](../../../backend/src/main/java/com/localmind/common/security/ProblemDetailSecurityHandler.java) | `security` | Writes 401/403 from the filter chain as `ProblemDetail` |
| [`CurrentUserId`](../../../backend/src/main/java/com/localmind/common/security/CurrentUserId.java) / [`CurrentUserIdArgumentResolver`](../../../backend/src/main/java/com/localmind/common/security/CurrentUserIdArgumentResolver.java) | `security` | Injects the caller's user id into controller methods |
| [`JwtProperties`](../../../backend/src/main/java/com/localmind/common/security/JwtProperties.java) / [`CorsProperties`](../../../backend/src/main/java/com/localmind/common/security/CorsProperties.java) | `security` | `localmind.jwt` / `localmind.cors` |
| [`TransactionHooks`](../../../backend/src/main/java/com/localmind/common/transaction/TransactionHooks.java) | `transaction` | Runs side effects after commit or on rollback |

## Security

See [security.md](../security.md) for the full model. In short:

- **Filter chain:** CSRF disabled, stateless sessions, OAuth2 resource server validating JWTs with the local RSA public
  key and the issuer `SecurityConfig.TOKEN_ISSUER` (`localmind`, `JwtValidators.createDefaultWithIssuer`). Public
  paths: `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/error`,
  `/actuator/health`. `ASYNC` and `ERROR` dispatches are permitted so SSE
  streams and error rendering are not re-authenticated mid-response. Everything else requires a valid token.
- **Signing key:** `SecurityConfig#rsaKeyPair` loads `localmind.jwt.key-file`, or generates a 2048-bit RSA key and
  writes it as PKCS#8 PEM. On POSIX file systems the file is created with `rw-------` in the same call
  (`Files.newByteChannel` with a permission attribute), so it is never briefly readable by others; on Windows the
  directory's ACLs apply. The JWK key id is the key's
  thumbprint, so it is stable across restarts.
- **`@CurrentUserId`:** resolves the `userId` claim, falling back to the token subject, then to
  `Authentication#getName()`. With no authentication (or no name) it throws `UnauthorizedException`
  `No authenticated user in security context`.
- **CORS:** origins from `localmind.cors.allowed-origins`; methods `GET POST PATCH DELETE PUT OPTIONS`; headers
  `Authorization Content-Type Accept X-Requested-With`; no exposed response headers; credentials allowed; preflight
  cached for 3600 s.
- **Passwords:** `BCryptPasswordEncoder` with default strength.

## Errors

See [errors.md](../errors.md). Every 403, whether from an ownership check (`GlobalExceptionHandler`) or from the
filter chain (`ProblemDetailSecurityHandler`), uses `ProblemDetails.ACCESS_DENIED_MESSAGE`
(`You do not have access to this resource`).

## Transaction hooks

`TransactionHooks.onOutcome(afterCommit, afterRollback)` registers a `TransactionSynchronization`:

- `afterCommit` runs once the transaction commits; `afterRollback` runs if it rolls back.
- With no active transaction synchronization, `afterCommit` runs immediately and `afterRollback` never runs.
- Callback exceptions are logged at `WARN` and swallowed, because the transaction outcome is already final.
- `TransactionHooks.afterCommit(action)` registers a synchronization that only runs `action` after commit (or
  immediately without an active transaction); nothing runs on rollback.
- `TransactionHooks.onRollback(action)` runs `action` only if the transaction rolls back, to undo an external write
  made inside it (`UserMemoryWriteService` deletes the embedding it added); without an active transaction it never runs.

Features use it to start `@Async` jobs only once their rows are visible, to delete files and vectors only once a delete
has committed, and to remove an already-stored upload if the transaction fails.

## Vector-store conventions

All vectors live in one Qdrant collection (`localmind.qdrant.collection-name`). Every point carries:

| Payload key (`VectorMetadata`) | Value |
| --- | --- |
| `userId` | Owner's user id, on every point |
| `type` | `DOC_CHUNK` (document chunk) or `USER_MEMORY` (long-term memory) |
| `conversationId` | Conversation of a document chunk |
| `attachmentId` | Attachment a chunk came from |
| `memoryId` | Id of the `UserMemory` row a memory vector mirrors |
| `fileName` | Original file name of the chunk's attachment |
| `chunkIndex` | Position of the chunk within its document |

`VectorFilters` builds the only filters searches may use, so every search is scoped to the caller:

| Method | Filter |
| --- | --- |
| `documentChunks(userId, conversationId)` | `userId == ? AND conversationId == ? AND type == DOC_CHUNK` |
| `userMemories(userId)` | `userId == ? AND type == USER_MEMORY` |
| `userMemory(userId, memoryId)` | `userId == ? AND memoryId == ? AND type == USER_MEMORY` |

At startup `QdrantConfig` creates keyword payload indexes on `userId`, `conversationId`, `type`, `memoryId` and `attachmentId`. Failures
(index already exists, Qdrant down) are logged at `DEBUG` and ignored; an interrupt while waiting restores the
thread's interrupt flag. The collection itself is created by Spring AI
(`spring.ai.vectorstore.qdrant.initialize-schema: true`) with the embedding model's dimension.

## Context keys

`ChatContextKeys` holds the keys put into the advisor context and the tool context of a chat request: `userId`,
`conversationId`, `currentMessageId`, `modelProfile`, `requestSystemPrompt`. `getString(context, key)` returns the value
only if it is a non-blank `String`, else `null`.

## HTTP clients and MVC

- **Async request timeout:** 3 minutes, so slow local models can finish streaming.
- **`shortTimeoutRestClient`:** a clone of Spring Boot's `RestClient.Builder` with 5 s connect and read timeouts, used
  for the Ollama model list and SearXNG. The shared builder is never customised, because Spring AI builds its Ollama
  client from it and model calls need far longer timeouts.

## Configuration

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `localmind.jwt.*` | `JWT_*` | see [configuration.md](../configuration.md#jwt-localmindjwt) | Token lifetimes, cookie flags, key file |
| `localmind.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://127.0.0.1:3000` | Allowed browser origins |
| `localmind.qdrant.collection-name` | `QDRANT_COLLECTION` | `localmind_vectors` | Collection that receives payload indexes |

Blank values (an environment variable set to an empty string) are treated like missing ones: `JwtProperties`
(`cookie-same-site`, `key-file`) and `QdrantProperties` fall back to their defaults and trim configured values;
`CorsProperties` drops blank origins, trims the rest and falls back to the default origins when none remain.

## Tests

| Test class | What it covers |
| --- | --- |
| [`JwtSigningKeyStoreTest`](../../../backend/src/test/java/com/localmind/common/security/JwtSigningKeyStoreTest.java) | The key generated on first start is persisted and reloaded unchanged on the next start; on POSIX the file is `rw-------` |
| [`JwtPropertiesTest`](../../../backend/src/test/java/com/localmind/common/security/JwtPropertiesTest.java) | Defaults for missing and blank values; configured values trimmed |
| [`CorsPropertiesTest`](../../../backend/src/test/java/com/localmind/common/security/CorsPropertiesTest.java) | Default origins for missing, empty or all-blank lists; blank entries dropped |
| [`QdrantPropertiesTest`](../../../backend/src/test/java/com/localmind/common/config/QdrantPropertiesTest.java) | Default collection for missing or blank names |
| [`CurrentUserIdArgumentResolverTest`](../../../backend/src/test/java/com/localmind/common/security/CurrentUserIdArgumentResolverTest.java) | `userId` claim, then subject, then authentication name; 401 without authentication or name |
| [`ProblemDetailSecurityHandlerTest`](../../../backend/src/test/java/com/localmind/common/security/ProblemDetailSecurityHandlerTest.java) | Filter-chain 403 and 401 written as ProblemDetail with `message` and `WWW-Authenticate` |
| [`GlobalExceptionHandlerTest`](../../../backend/src/test/java/com/localmind/common/exception/GlobalExceptionHandlerTest.java) | Validation messages joined with `"; "` (fallback `Validation failed`), `ApiException` status, 403, `DataIntegrityViolationException` to 409, unexpected exception to 500 with a generic message |
| [`TransactionHooksTest`](../../../backend/src/test/java/com/localmind/common/transaction/TransactionHooksTest.java) | After-commit only on commit, rollback action only on rollback, immediate run without a transaction, callback failures swallowed |
