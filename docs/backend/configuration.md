# Configuration

All settings live in [`application.yml`](../../backend/src/main/resources/application.yml). Every value is an
environment-variable placeholder `${VAR:default}` with a working local default, so the backend starts against the
Docker stack in [`docker/`](../../docker) with no extra setup. Docker Compose uses `${VAR:-default}` and the same
variable names wherever both sides need the same value, and every Compose variable is listed in
[`docker/.env.example`](../../docker/.env.example); the variables only Compose reads are listed under
[Compose-only variables](#compose-only-variables).

LocalMind's own settings sit under `localmind.*` and are bound to `@ConfigurationProperties` records, which apply
defaults in their compact constructor. Where a `localmind.*` key mirrors a Spring key, both read the same variable, so
one override changes both.

## LocalMind settings

### JWT (`localmind.jwt`)

Record: [`JwtProperties`](../../backend/src/main/java/com/localmind/common/security/JwtProperties.java)

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `access-token-expiration` | `JWT_ACCESS_TOKEN_EXPIRATION` | `15m` | Lifetime of access tokens |
| `refresh-token-expiration` | `JWT_REFRESH_TOKEN_EXPIRATION` | `7d` | Lifetime of refresh tokens and their cookie |
| `cookie-secure` | `JWT_COOKIE_SECURE` | `false` | `Secure` flag on the refresh cookie; set `true` behind HTTPS |
| `cookie-same-site` | `JWT_COOKIE_SAME_SITE` | `Strict` | `SameSite` attribute of the refresh cookie; blank falls back to `Strict` |
| `key-file` | `JWT_KEY_FILE` | `./data/jwt-signing-key.pem` | RSA signing key (PKCS#8 PEM), generated on first start with owner-only `rw-------` permissions (POSIX); blank falls back to the default |

### CORS (`localmind.cors`)

Record: [`CorsProperties`](../../backend/src/main/java/com/localmind/common/security/CorsProperties.java)

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `allowed-origins` | `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://127.0.0.1:3000` | Browser origins allowed to call the API with credentials; blank entries are dropped and an empty list falls back to the default |

### LLM (`localmind.llm`)

Record: [`LlmProperties`](../../backend/src/main/java/com/localmind/llm/config/LlmProperties.java)

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `default-model` | `OLLAMA_CHAT_MODEL` | `gemma3:1b` | Chat model used when a request and conversation name none |
| `default-embedding-model` | `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | Embedding model expected in Ollama |
| `ollama-base-url` | `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` | Ollama URL for the model catalogue (trimmed, trailing `/` stripped) |
| `validate-models-on-startup` | `VALIDATE_MODELS_ON_STARTUP` | `true` (also when the key is absent) | Fail startup if Ollama is unreachable, older than 0.30.0 or the default models are not pulled; when `false`, only warn |

Blank `default-model` / `default-embedding-model` / `ollama-base-url` values fall back to the defaults above.

### Qdrant (`localmind.qdrant`)

Record: [`QdrantProperties`](../../backend/src/main/java/com/localmind/common/config/QdrantProperties.java)

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `collection-name` | `QDRANT_COLLECTION` | `localmind_vectors` | Collection that receives payload indexes; mirrors `spring.ai.vectorstore.qdrant.collection-name`; blank falls back to the default |

### File storage (`localmind.storage`)

Record: [`FileStorageProperties`](../../backend/src/main/java/com/localmind/attachment/config/FileStorageProperties.java)

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `upload-dir` | `UPLOAD_DIR` | `./uploads` | Root directory for uploaded files |
| `max-file-size` | `UPLOAD_MAX_FILE_SIZE` | `25MB` | Per-file limit checked by the attachment service; mirrors `spring.servlet.multipart.max-file-size` |
| `allowed-extensions` | `UPLOAD_ALLOWED_EXTENSIONS` | `pdf,txt,docx,md` | Accepted extensions, a subset of `SupportedFileType` (`pdf, txt, docx, md`, also the default); each value is trimmed and lower-cased (`Locale.ROOT`) and blank values are dropped; an unsupported value fails startup |

### Prompt assembly (`localmind.prompt`)

Record: [`PromptProperties`](../../backend/src/main/java/com/localmind/chat/config/PromptProperties.java). Each switch
turns one advisor on or off; see [chat](features/chat.md#prompt-advisors).

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `rag.auto` | `PROMPT_RAG_AUTO` | `true` | Inject document excerpts for conversations with a `READY` attachment (`AutoRagAdvisor`) |
| `memory.semantic` | `PROMPT_MEMORY_SEMANTIC` | `true` | Inject relevant long-term memories (`MemoryRetrievalAdvisor`) |
| `history.token-trim` | `PROMPT_HISTORY_TOKEN_TRIM` | `true` | Trim history to the model's token budget (`HistoryAdvisor`) |
| `stats.enabled` | `PROMPT_STATS_ENABLED` | `true` | Log per-role token statistics (`PromptStatsAdvisor`) |

### Tools (`localmind.tools`)

Records: [`SearxngProperties`](../../backend/src/main/java/com/localmind/tools/config/SearxngProperties.java),
[`WebScraperProperties`](../../backend/src/main/java/com/localmind/tools/config/WebScraperProperties.java)

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `searxng.base-url` | `SEARXNG_BASE_URL` | `http://127.0.0.1:${SEARXNG_PORT:8081}` (record: `http://127.0.0.1:8081`) | SearXNG instance for web search; the default follows the port Compose publishes SearXNG on. Trimmed, trailing `/` stripped, blank falls back to the default |
| `scraper.allow-private-subnets` | `SCRAPER_ALLOW_PRIVATE_SUBNETS` | `false` | Disables the scraper's SSRF guard. Local testing only |

## Spring settings

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `server.port` | `SERVER_PORT` | `8080` | HTTP port |
| `spring.threads.virtual.enabled` | — | `true` | Requests and `@Async` tasks run on virtual threads |
| `spring.http.clients.imperative.factory` | — | `reactor` | Request factory of the shared `RestClient.Builder` that Spring AI's Ollama client uses. Pinned because `httpclient5` (needed by the web scraper) would otherwise be auto-detected first |
| `spring.servlet.multipart.max-file-size` | `UPLOAD_MAX_FILE_SIZE` | `25MB` | Multipart limit per file |
| `spring.servlet.multipart.max-request-size` | `UPLOAD_MAX_REQUEST_SIZE` | `30MB` | Multipart limit per request |
| `spring.datasource.url` | `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE` | `127.0.0.1`, `3306`, `localmind` | MySQL JDBC URL (`useSSL=false`, UTC) |
| `spring.datasource.username` | `MYSQL_USER` | `localmind` | Database user |
| `spring.datasource.password` | `MYSQL_PASSWORD` | `localmind` | Database password |
| `spring.jpa.hibernate.ddl-auto` | `JPA_DDL_AUTO` | `update` | Hibernate schema management |
| `spring.jpa.show-sql` | `JPA_SHOW_SQL` | `false` | Log SQL |
| `spring.jpa.open-in-view` | — | `false` | No lazy loading outside services |
| `spring.ai.ollama.base-url` | `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` | Ollama URL used by Spring AI |
| `spring.ai.ollama.chat.options.model` | `OLLAMA_CHAT_MODEL` | `gemma3:1b` | Spring AI's default chat model |
| `spring.ai.ollama.embedding.options.model` | `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | Embedding model |
| `spring.ai.vectorstore.qdrant.host` | `QDRANT_HOST` | `127.0.0.1` | Qdrant host |
| `spring.ai.vectorstore.qdrant.port` | `QDRANT_GRPC_PORT` | `6334` | Qdrant gRPC port |
| `spring.ai.vectorstore.qdrant.collection-name` | `QDRANT_COLLECTION` | `localmind_vectors` | Vector collection |
| `spring.ai.vectorstore.qdrant.initialize-schema` | — | `true` | Spring AI creates the collection |
| `management.endpoints.web.exposure.include` | — | `health,metrics` | Exposed Actuator endpoints (only `health` is public) |
| `management.endpoint.health.show-details` | — | `when-authorized` | Health details only for authenticated callers |

## Compose-only variables

[`docker/docker-compose.yml`](../../docker/docker-compose.yml) also reads these variables, which the backend never
reads. All of them are listed in `docker/.env.example`.

| Env var | Default | Effect |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | `admin` | MySQL root password, also used by the container health check. The backend connects as `MYSQL_USER` |
| `QDRANT_HTTP_PORT` | `6333` | Host port of Qdrant's REST API and dashboard. The backend uses gRPC (`QDRANT_GRPC_PORT`) |
| `SEARXNG_PORT` | `8081` | Host port SearXNG is published on. Not bound to a property itself, but the default of `localmind.tools.searxng.base-url` uses it, so changing it in both environments keeps web search working; an explicit `SEARXNG_BASE_URL` wins |
| `SEARXNG_SECRET` | `localmind_searxng_secret_key` | SearXNG's own secret key |
| `MYSQL_IMAGE_TAG`, `QDRANT_IMAGE_TAG`, `SEARXNG_IMAGE_TAG` | see [Docker images](#docker-images) | Pinned image versions |

## Test profile

[`application-test.yml`](../../backend/src/test/resources/application-test.yml) (`@ActiveProfiles("test")`) runs the
context with no external services: in-memory H2 in MySQL mode with `create-drop`, no Qdrant schema initialisation,
JWT key and uploads under `java.io.tmpdir`, and `validate-models-on-startup: false`. The profile alone does not stop
beans from calling Ollama or Qdrant, so every non-live `@SpringBootTest` also carries
[`@OfflineAiStack`](../../backend/src/test/java/com/localmind/support/OfflineAiStack.java), which replaces
`QdrantClient`, `VectorStore`, `EmbeddingModel`, `ChatModel`, `ModelCatalogService` and `OllamaVersionCheck` with
Mockito mocks. The startup probes then do nothing and no test opens a connection to either service.

Unit tests share hand-written doubles from `support/` instead of declaring their own:
[`RecordingVectorStore`](../../backend/src/test/java/com/localmind/support/RecordingVectorStore.java) (records adds,
searches and filter deletes, returns canned results, can fail on add or search),
[`StubChatModel`](../../backend/src/test/java/com/localmind/support/StubChatModel.java) (canned `call` reply and
`stream` chunks, keeps the last prompt) and
[`OfflineModelCatalog`](../../backend/src/test/java/com/localmind/support/OfflineModelCatalog.java).

## Docker images

Compose pins every image through a `*_IMAGE_TAG` variable: `mysql:8.4`, `qdrant/qdrant:v1.20.1`,
`searxng/searxng:2026.11.12-de722ab1b`. Change a version on purpose by updating both the Compose default and
`docker/.env.example`.
