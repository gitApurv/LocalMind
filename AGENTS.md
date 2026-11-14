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

## Frontend Coding Pattern (Next.js, `frontend/`)

Every frontend change must follow this pattern. When touching code that predates it, bring that code in line. Formatting is covered by "Formatting Rules" above (Prettier, 2 spaces, 100 columns, LF).

### 1. Feature folders

Code is grouped first by feature, mirroring the backend's package-by-feature, then by layer inside each feature. Every feature uses the same layer folders and creates only the ones it needs:

| Folder | Contains |
| --- | --- |
| `api/` | `<feature>-api.ts`: typed functions over `apiClient`, the only place backend URLs appear |
| `components/` | React components of the feature |
| `hooks/` | `use-*.ts` hooks that combine stores, effects and APIs (streaming, polling, bootstrap) |
| `store/` | Zustand stores (`<name>-store.ts`) |
| `lib/` | Pure logic without React, stores or `apiClient`: parsers, validation, formatting |
| `types.ts` | Types mirroring the backend DTOs and requests of this feature |
| `constants.ts` | Literals used in more than one file (default titles, model presets, suggestions) |

```text
frontend/src
├── app/                    # Next.js routes only: layout.tsx, page.tsx, login/, register/
├── components/ui/          # design-system primitives (Button, Card, Input, Alert), no feature logic
├── lib/                    # cross-cutting, no feature logic
│   ├── api-client.ts       # fetch wrapper: bearer token, refresh cookie, 401 refresh queue
│   ├── api-error.ts        # ApiError, getErrorMessage(), isAbortError()
│   └── utils.ts            # cn()
├── features/
│   ├── auth/               # login, register, session refresh, profile
│   │   ├── api/  components/  hooks/  lib/  store/  types.ts
│   ├── workspace/          # authenticated app shell: auth gate, sidebar, user menu
│   │   ├── components/  hooks/
│   ├── conversations/      # thread list, create/rename/pin/delete, active thread
│   │   ├── api/  components/  store/  constants.ts  types.ts
│   ├── chat/               # messages, SSE streaming, composer, markdown rendering
│   │   ├── api/  components/  hooks/  lib/  store/  constants.ts  types.ts
│   ├── attachments/        # document upload and ingestion status polling
│   │   ├── api/  components/  hooks/  lib/  types.ts
│   ├── models/             # model catalogue, model selection, tools toggle
│   │   ├── api/  components/  lib/  store/  constants.ts  types.ts
│   └── settings/           # profile and custom-instructions dialog
│       └── components/
└── test/                   # shared test factories (aConversation, resetWorkspaceStores)
```

- A feature may import another feature's `api`, `store`, `hooks`, `components` and `types`. `lib/` and `components/ui/` import no feature. No import cycles between files.
- Import other features and shared code with the `@/` alias (`@/features/chat/store/chat-store`), files of the same feature with relative paths (`./`, `../store/...`). No barrel `index.ts` files.
- One exported component per file (small private sub-components may stay in the same file).

### 2. Layer responsibilities

| Layer | Does | Does not |
| --- | --- | --- |
| Route (`app/**/page.tsx`, `layout.tsx`) | Metadata, compose one feature component | Hold state, call APIs, contain feature markup |
| Component | Render, local UI state (`useState`), read stores via selectors, call store actions or hooks | Call `apiClient`/`fetch`, know URLs, parse server data |
| Hook (`useX`) | Effects and orchestration shared by components: streaming, polling, bootstrap | Render JSX |
| Store (`useXStore`) | State shared across components and the async actions that change it | Touch the DOM, use React hooks, build URLs |
| API (`xApi`) | One typed function per endpoint, returning the parsed body | Hold state, catch errors, show UI |
| `lib/` function | Pure logic, unit-testable without React | Import React, stores or `apiClient` |

### 3. Naming

- Files and folders are `kebab-case`: `conversation-list.tsx`, `use-chat-stream.ts`, `chat-store.ts`, `chat-api.ts`.
- Components are `PascalCase` named exports (`export function ConversationList`). Default exports only where Next.js requires them (route files).
- Props types are `XProps`; hooks `useX`; stores `useXStore`; API objects `xApi`; request bodies `XRequest`; domain types use the backend's noun without suffix (`Conversation` for `ConversationResponse`).
- Constants are `UPPER_SNAKE_CASE` and live in the feature's `constants.ts`. No duplicated literals (default title `"New Chat"`, model names, tool names).

### 4. State & data fetching

- Zustand holds only state shared by more than one component; everything else is local `useState`.
- Read stores with one selector per value: `const messages = useChatStore((s) => s.messages)`. Never destructure the whole store (`const { a, b } = useStore()`), which re-renders on every change. Outside React, use `useXStore.getState()`.
- Async actions live in the store and call the feature's `api/`. A store may call another feature's store through `getState()` inside an action (e.g. conversations resets the chat store on switch); stores never subscribe to each other.
- Server data used by a single component subtree (e.g. attachments with polling) is loaded in a feature hook, not a store.
- The browser calls same-origin `/api/**`; `next.config.mjs` rewrites to `BACKEND_API_URL`. Only `lib/api-client.ts` and `api/` modules issue requests; streaming goes through `apiClient.stream()` so it gets the same headers, refresh and error handling.

### 5. Errors

- `apiClient` throws `ApiError { status, message, errors? }`: `message` comes from the backend's ProblemDetail `message` property, `status` 0 means a network failure. An aborted request rethrows the original `AbortError`.
- Catch as `unknown` and read the text with `getErrorMessage(err, fallback)`. No `any` (enforced by ESLint).
- Actions whose result the UI shows (login, save profile, create conversation) set the store `error` and rethrow; background loads (lists, models) log with `console.error` and keep the previous state.
- User-visible errors are rendered with the `Alert` primitive; never `alert()`.

### 6. Styling

- Tailwind utilities with the theme tokens from `app/globals.css` (`bg-background`, `text-primary`, `border-border`); no raw hex colours except syntax highlighting.
- Tailwind scans `src/**` (`tailwind.config.ts`); keep class names as literal strings so it can find them.
- Combine conditional classes with `cn()`, not template strings. Reusable variants use `cva` in `components/ui/`.
- Accessibility: every interactive element is a real `button`/`input` or has a role, and icon-only buttons have an `aria-label`.

### 7. Tests

- Vitest + Testing Library on jsdom. Tests sit in a `__tests__/` folder next to the file under test, named `<file>.test.ts(x)`.
- Mock HTTP with `vi.spyOn(apiClient, ...)` or a stubbed `global.fetch`; never mock the module under test. Reset every store used with `setState` in `beforeEach` (`resetWorkspaceStores()` from `@/test/factories`) and build fixtures with the factories there.
- Query by role, label or visible text, not by class names.
- Every behaviour change ships with a test. Before committing, all of these pass in `frontend/`: `npm run lint`, `npm run typecheck`, `npm test`, `npm run format:check`, `npm run build`.
