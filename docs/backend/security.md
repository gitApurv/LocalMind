# Security

How the backend authenticates callers, isolates users from each other and guards its outbound calls. For the threat
model and its rationale, see [DESIGN.md §8](../DESIGN.md#8-threat-model--security-posture-stride).

## Authentication

| Aspect | Implementation | Source |
| --- | --- | --- |
| Access tokens | RS256 JWT issued by the backend itself, validated as an OAuth2 resource server | [`SecurityConfig`](../../backend/src/main/java/com/localmind/common/security/SecurityConfig.java), [`JwtService`](../../backend/src/main/java/com/localmind/auth/service/JwtService.java) |
| Signing key | RSA-2048, PKCS#8 PEM at `localmind.jwt.key-file`, generated on first start and created with owner-only `rw-------` permissions in the same call (POSIX file systems; on Windows the directory ACLs apply), so it is never readable by others; key id = JWK thumbprint | [`JwtSigningKeyStore`](../../backend/src/main/java/com/localmind/common/security/JwtSigningKeyStore.java) |
| Refresh tokens | Opaque random value in an `HttpOnly` cookie scoped to `/api/auth`; only its SHA-256 hash is stored; rotated on every use with reuse detection | [auth-session flow](flows/auth-session.md) |
| Passwords | BCrypt (default strength); 8–72 characters and at most 72 UTF-8 bytes | [`AuthService`](../../backend/src/main/java/com/localmind/auth/service/AuthService.java) |
| Sessions | Stateless (`SessionCreationPolicy.STATELESS`); CSRF protection disabled because the API authenticates with bearer tokens, and the refresh cookie is `SameSite` and path-scoped (a blank `JWT_COOKIE_SAME_SITE` falls back to `Strict`, never an empty attribute) | `SecurityConfig` |

Token validation uses `JwtValidators.createDefaultWithIssuer(SecurityConfig.TOKEN_ISSUER)`: signature, `exp`/`nbf` and
`iss == "localmind"`. `JwtService` sets the same constant as the issuer, so a token signed with the LocalMind key but
issued by anyone else is rejected with 401.

`AuthService.login` always runs exactly one BCrypt check: against the user's hash, or, when the email is unknown,
against a dummy hash created lazily on first use (`SingletonSupplier`, BCrypt of a random UUID). An unknown email
therefore takes as long as a wrong password and response times do not reveal which emails are registered; both
return 401 `Invalid email or password`.

## Authorisation

### Public paths

| Path | Why |
| --- | --- |
| `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` | Need no access token (refresh and logout use the cookie) |
| `/error` | Spring's error dispatch |
| `/actuator/health` | Liveness probe; details only for authenticated callers |

`ASYNC` and `ERROR` dispatcher types are permitted so SSE responses and error rendering are not re-authorised after the
original request passed.

The auth paths are listed one by one rather than as `/api/auth/**`, so `GET`/`PATCH /api/auth/me` require a valid
bearer token like every other endpoint.

### Per-user isolation

Every resource belongs to one user (`user_id` column or `userId` vector payload). Isolation is enforced in services:

| Resource | Check | Result on mismatch |
| --- | --- | --- |
| Conversation, its messages and attachments | [`OwnershipGuard`](../../backend/src/main/java/com/localmind/conversation/service/OwnershipGuard.java) before any lookup; upload, conversation delete and the start of a chat turn use `lockOwnedConversation` (`PESSIMISTIC_WRITE`) | 403 (also for ids that do not exist) |
| Memory | Lookup by `id`, then owner check in `MemoryService` | 403 (also for ids that do not exist) |
| Attachment download | Conversation ownership, then owner check after `findById` in `AttachmentService` | 403 for unknown ids, another user's attachment or another conversation's |
| Vectors | Every search uses [`VectorFilters`](../../backend/src/main/java/com/localmind/common/ai/VectorFilters.java) with the caller's `userId` | Other users' points are never returned |
| Tools | Read `userId`/`conversationId` from the tool context set by the orchestrator, never from model arguments | Model cannot address another user's data |

## CORS

Origins from `localmind.cors.allowed-origins` (blank entries dropped; an empty list falls back to
`http://localhost:3000`, `http://127.0.0.1:3000`), credentials allowed, no exposed response headers, methods
`GET POST PATCH DELETE PUT OPTIONS`, headers `Authorization Content-Type Accept X-Requested-With`. In the default
setup the browser calls the Next.js origin, which proxies `/api/**`, so CORS only matters when the API is called
cross-origin.

## Outbound requests

| Caller | Target | Guard |
| --- | --- | --- |
| `ModelCatalogService` | Ollama `/api/tags` | Fixed URL from config, 5 s timeouts |
| `WebSearchTools` | SearXNG `/search` | Fixed URL from config, 5 s timeouts |
| `WebScraperTools` | URL chosen by the model | SSRF guard on every hop, enforced in the HTTP client's DNS resolver so the connection uses exactly the checked addresses (no DNS rebinding): no automatic redirects (at most 5, followed manually), each target rejected if any address is loopback, private, link-local, unspecified/`0.0.0.0/8`, CGNAT, `192.0.0.0/24`, `198.18.0.0/15`, `240.0.0.0/4` (incl. `255.255.255.255`), multicast, IPv6 unique-local, Teredo `2001::/32` or local-use NAT64 `64:ff9b:1::/48`; IPv4-mapped, IPv4-compatible, NAT64 `64:ff9b::/96` and 6to4 `2002::/16` addresses are judged by their embedded IPv4; 10 s timeouts, 2 MiB body cap. Disabled only by `localmind.tools.scraper.allow-private-subnets` |

Details: [tools.md — SSRF defence](features/tools.md#ssrf-defence).

## Uploads

- Extension allow-list and size limit ([attachment](features/attachment.md)).
- Stored names are reduced to their last path segment, restricted to `[a-zA-Z0-9._-]` and prefixed with the
  attachment id; a path-traversal check rejects anything resolving outside the user's directory.
- File names longer than 255 characters are rejected (400).
- The stored and served content type is derived from the extension, never from the client's `Content-Type`.
- Downloads are sent with `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff`.

## Error hygiene

Exception text never reaches clients, SSE events, stored status messages or tool results; see
[errors.md](errors.md).
