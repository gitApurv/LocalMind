# Project Guidelines & Agent Rules

## Formatting Rules (all files, every OS)

The repository must look byte-for-byte the same on macOS, Linux and Windows. These rules apply to every file, backend and frontend:

- **Encoding:** UTF-8, no BOM.
- **Line endings:** LF everywhere. The only exception is `*.cmd`/`*.bat`, which use CRLF. `.gitattributes` (`* text=auto eol=lf`) enforces this in git regardless of `core.autocrlf`; never commit CRLF files.
- **Whitespace:** spaces only (no tabs, except in Makefiles), no trailing whitespace (Markdown excepted), one final newline.
- **Indentation:** 4 spaces for Java, Python and PowerShell; 2 spaces for TypeScript/JavaScript, JSON, YAML, CSS, HTML and shell.
- **Line length:** 120 for Java, 100 for frontend code.

Sources of truth (keep them consistent when changing a rule):

| File | Governs |
| --- | --- |
| `.gitattributes` | Line endings stored in git and checked out |
| `.editorconfig` | Editor defaults (charset, EOL, indentation, final newline) |
| `backend/eclipse-formatter.xml` + Spotless in `backend/pom.xml` | Java formatting, import order, unused imports, LF output |
| `.prettierrc.json` | Frontend formatting (`endOfLine: lf`) |

Commands:

- Backend: `./mvnw spotless:apply` formats; `./mvnw verify` fails if anything is unformatted.
- Frontend: `npm run format` formats; `npm run format:check` verifies.
- Run the formatter before every commit; do not hand-format code against these tools.

## Backend Coding Pattern (Spring Boot, `backend/`)

Every backend change must follow this pattern. When touching code that predates it, bring that code in line.

### 1. Package by feature

Code is grouped first by business capability (feature), then by layer inside each feature. Every feature uses the same layer sub-packages and creates only the ones it needs:

| Sub-package | Contains |
| --- | --- |
| `controller/` | `@RestController` classes |
| `service/` | `@Service`/`@Component` business logic (including guards and helpers used only by services) |
| `repository/` | Spring Data repositories |
| `entity/` | JPA entities |
| `dto/` | Request/response records exposed over HTTP |
| `model/` | Internal value objects/records that are not entities and not HTTP DTOs |
| `config/` | `@ConfigurationProperties` records and feature-specific `@Configuration` |

A feature may add one purpose-named component sub-package when a group of classes forms its own pipeline or infrastructure (`chat/prompt`, `attachment/storage`). Nothing else lives at a feature's root.

```text
com.localmind
├── LocalMindApplication.java
├── common/                 # cross-cutting code, no business logic
│   ├── ai/                 # ChatContextKeys, VectorMetadata, VectorFilters
│   ├── config/             # WebMvcConfig (shortTimeoutRestClient), QdrantConfig, QdrantProperties
│   ├── exception/          # ApiException hierarchy, ProblemDetails, GlobalExceptionHandler
│   ├── security/           # SecurityConfig, JWT/CORS properties, signing-key store, @CurrentUserId
│   └── transaction/        # TransactionHooks: side effects after commit / rollback
├── auth/                   # /api/auth/**: register, login, refresh, logout, profile
│   ├── controller/  dto/  entity/  model/  repository/  service/
├── user/                   # entity/User, repository/UserRepository
├── memory/                 # /api/memories: list, pin, delete long-term memories
│   ├── controller/  dto/  entity/  repository/  service/
├── conversation/           # conversations, messages, ownership guard, titling, summaries
│   ├── controller/  dto/  entity/  repository/  service/
├── attachment/             # uploads and document ETL into the vector store
│   ├── config/  controller/  dto/  entity/  repository/  service/
│   └── storage/            # FileStorageService abstraction + local-disk implementation
├── chat/                   # streaming chat orchestration and SSE events
│   ├── config/  controller/  dto/  service/
│   └── prompt/             # Spring AI advisors that assemble the prompt, ToolSelector
├── llm/                    # model catalogue (/api/models) and model profiles
│   ├── config/  controller/  dto/  model/  service/
└── tools/                  # LLM-callable tools
    ├── config/  service/
```

- One top-level type per file. No `*Dtos` holder classes.
- A feature may depend on another feature's entities, repositories and services, but never on its controllers. `common` depends on no feature.
- Tests mirror the package of the class under test (`auth/service/AuthServiceTest`); cross-feature tests live in `integration/`.

### 2. Layer responsibilities

| Layer | Does | Does not |
| --- | --- | --- |
| Controller (`XController`) | Map HTTP ↔ DTOs, validate with `@Valid`, read the caller with `@CurrentUserId`, set status codes/headers | Contain business rules, touch repositories, catch exceptions |
| Service (`XService`) | Business rules, transactions (`@Transactional` on public methods, `readOnly = true` for queries), ownership checks, entity ↔ DTO mapping | Know about `HttpServletRequest`/`ResponseEntity` |
| Repository (`XRepository`) | Spring Data interfaces, derived queries, `@Modifying` JPQL for bulk deletes and targeted updates | Contain logic |
| Entity | JPA mapping, `@PrePersist` defaults | Expose business workflows |
| DTO | Immutable `record`, Bean Validation annotations, `static from(Entity)` factory for responses | Hold entities or mutable state |

- Return the DTO directly from a controller and use `@ResponseStatus` for non-200 statuses. Use `ResponseEntity` only when headers (cookies, content-disposition) must be set.
- Long-running work runs in `@Async` services and is triggered after commit.
- Side effects outside the database (files, Qdrant, async jobs) go through `common.transaction.TransactionHooks`: run them after commit, and undo anything already done outside the database on rollback (e.g. delete a stored upload). Deleting a row also deletes its files and vectors.
- Code that runs outside the request transaction (end of a stream, `@Async` jobs) never saves an entity loaded earlier; it uses a targeted `@Modifying` `UPDATE` of the columns it owns (`ConversationRepository#touch`, `#replacePlaceholderTitle`, `#updateSummary`) so concurrent user edits are not overwritten.
- Request DTO validation mirrors the column definitions (`@Size(max = …)` equal to the column length), so oversized input is a 400, never a database error.

### 3. Dependency injection & Lombok

- Constructor injection only, via Lombok `@RequiredArgsConstructor` on `final` fields. No field `@Autowired`, no setter injection. A class that needs an extra package-private constructor for tests marks its injection constructor with `@Autowired`.
- Injected collaborators are never `null`: do not write `if (repository != null)` guards.
- Loggers: Lombok `@Slf4j`.
- Entities: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, with `@Builder.Default` for initialised fields. Never `@Data`, `@EqualsAndHashCode` or `@ToString` on entities.
- DTOs, value objects and configuration properties are `record`s, not Lombok classes.

### 4. Configuration

- No `@Value` anywhere. Every setting is bound to a `@ConfigurationProperties` record (scanned by `@ConfigurationPropertiesScan`) under the `localmind.*` prefix, with defaults applied in the compact constructor. The record lives in the feature that owns it:
  - `localmind.jwt` → `common.security.JwtProperties` (token lifetimes, cookie flags, signing-key file)
  - `localmind.cors` → `common.security.CorsProperties`
  - `localmind.qdrant` → `common.config.QdrantProperties`
  - `localmind.storage` → `attachment.config.FileStorageProperties`
  - `localmind.llm` → `llm.config.LlmProperties` (default model, Ollama base URL)
  - `localmind.prompt` → `chat.config.PromptProperties`
  - `localmind.tools.searxng` / `localmind.tools.scraper` → `tools.config.SearxngProperties` / `tools.config.WebScraperProperties`
- Externalised values use environment-variable placeholders with a working local default: `${VAR:default}` in `application.yml`, `${VAR:-default}` in `docker/docker-compose.yml`. The backend and compose share variable names (`MYSQL_PASSWORD`, `QDRANT_GRPC_PORT`, ...), and every compose variable is listed in `docker/.env.example`. When a `localmind.*` key mirrors a Spring key (e.g. `localmind.llm.ollama-base-url` and `spring.ai.ollama.base-url`), both use the same variable so one override changes both. Never commit real secrets; `.env` files are git-ignored.
- Docker images are pinned to an explicit version (`mysql:8.4`, `qdrant/qdrant:v1.20.1`, `searxng/searxng:<date>-<sha>`), never `latest`, so every machine runs the same stack and database volumes are never upgraded implicitly. Bump a version deliberately via its `*_IMAGE_TAG` default and `docker/.env.example`.
- Never customize the shared `RestClient.Builder` (no `RestClientCustomizer`, no `RestClient.Builder` bean): Spring AI builds its Ollama client from it, and embeddings or completions on slow hardware need long timeouts. LocalMind's own short HTTP calls inject the `shortTimeoutRestClient` bean from `WebMvcConfig`.
- `spring.main.allow-bean-definition-overriding` stays off; a duplicate bean is a bug to fix, not to hide.
- The JWT signing key lives in `localmind.jwt.key-file` (generated on first start, git-ignored under `backend/data/`) so tokens survive restarts. Only `/actuator/health` is public.
- Use Jackson 3 (`tools.jackson.databind.*`) for data binding; the annotations stay in `com.fasterxml.jackson.annotation`. Prefer typed `RestClient#body(SomeRecord.class)` over manual `ObjectMapper` parsing.
- No duplicated literals: model names, advisor/tool context keys (`ChatContextKeys`) and vector-store metadata keys (`VectorMetadata`) are constants in one place.

### 5. Errors

- Services throw subclasses of `common.exception.ApiException`: `ResourceNotFoundException` (404), `BadRequestException` (400), `UnauthorizedException` (401), `ConflictException` (409). Ownership violations throw Spring Security's `AccessDeniedException` (403).
- Never throw `ResponseStatusException` and never build error responses in controllers.
- Every error response is an RFC 9457 `ProblemDetail` with an extra `message` property (read by the frontend), built with `common.exception.ProblemDetails`:
  - `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`, so standard MVC errors (malformed JSON, upload too large, missing parameter, unknown route, ...) use the same format;
  - 401/403 raised by the security filter chain are written by `ProblemDetailSecurityHandler`;
  - anything unexpected becomes a 500 with a generic message.
- Never send exception text (`e.getMessage()`) to a client, an SSE `ERROR` event, a stored status message or a tool result: log the details and return a fixed, user-facing message.
- Tools and advisors never throw into the LLM pipeline: they log and return a readable error string (tools) or the unmodified request (advisors).
- Outbound fetches of model-chosen URLs validate every hop: no automatic redirects, each target re-checked against loopback, private, link-local, CGNAT, IPv6 unique-local and multicast ranges (`WebScraperTools`).

### 6. Code style

- Java formatting is defined by `backend/eclipse-formatter.xml` and applied by Spotless (see "Formatting Rules" below).
- Methods do one thing; extract private helpers when a method exceeds ~30 lines or nests more than two levels.
- Prefer early returns over nested `if`s, `Optional`/streams over manual null juggling, and `java.time` types for time.
- Comments explain *why*, not *what*. No commented-out code, no empty blocks.
- Naming: `XController`, `XService`, `XRepository`, `XRequest`/`XResponse` for DTOs, `XProperties` for configuration, `XAdvisor` for prompt advisors, `XTools` for LLM tools.

### 7. Tests

- Unit tests construct the class under test directly with mocks (`Mockito`) and real records for properties. Never let a unit test depend on a service that may or may not run on the machine (use test doubles such as `support/OfflineModelCatalog`).
- `@SpringBootTest` and `@WebMvcTest` classes use `@ActiveProfiles("test")`: `src/test/resources/application-test.yml` runs the context on in-memory H2 with no MySQL, Qdrant or Ollama.
- Tests that need the real stack are tagged `@Tag("live")` and gated with `@EnabledIfEnvironmentVariable(named = "LOCALMIND_LIVE_TESTS", matches = "true")`.
- Web tests use `MockMvc` with `SecurityMockMvcRequestPostProcessors.jwt()`.
- Every behaviour change ships with a test; `./mvnw verify` (tests + Spotless check) must pass before committing, with no infrastructure running.

