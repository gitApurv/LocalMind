# LocalMind backend documentation

Reference documentation for the Spring Boot backend in [`backend/`](../../backend). It describes what the code does
today; every statement links to or is checked against the source.

| If you want to... | Read |
| --- | --- |
| Understand how the backend is put together | [architecture.md](architecture.md) |
| Call the API | [api.md](api.md), then the feature page |
| Configure or deploy it | [configuration.md](configuration.md) |
| Understand authentication and isolation | [security.md](security.md), [flows/auth-session.md](flows/auth-session.md) |
| Know what is stored where | [data-model.md](data-model.md) |
| Handle errors in a client | [errors.md](errors.md) |
| Follow a message from send to stored answer | [flows/chat-streaming.md](flows/chat-streaming.md) |
| Follow an upload until it is searchable | [flows/document-ingestion.md](flows/document-ingestion.md) |
| See every prompt advisor in detail | [PROMPT_GENERATION_PROCESS.md](../PROMPT_GENERATION_PROCESS.md) |
| Understand why it was designed this way | [DESIGN.md](../DESIGN.md) |
| Follow the coding rules | [AGENTS.md](../../AGENTS.md) |

## Feature pages

One page per package under `com.localmind`, all with the same sections (responsibilities, package layout, HTTP API,
domain model, business rules, side effects, configuration, errors, interactions, tests).

| Feature | Page |
| --- | --- |
| `auth` | [features/auth.md](features/auth.md) |
| `user` | [features/user.md](features/user.md) |
| `conversation` | [features/conversation.md](features/conversation.md) |
| `chat` | [features/chat.md](features/chat.md) |
| `attachment` | [features/attachment.md](features/attachment.md) |
| `memory` | [features/memory.md](features/memory.md) |
| `llm` | [features/llm.md](features/llm.md) |
| `tools` | [features/tools.md](features/tools.md) |
| `common` | [features/common.md](features/common.md) |

## Running the backend

Start the infrastructure and pull the models as described in the [project README](../../README.md#quick-start-automated),
then from `backend/`:

```bash
./mvnw spring-boot:run
```

```bash
./mvnw verify
```

`verify` runs the unit and web tests on H2 with no infrastructure (profile `test`) and the Spotless check. Tests that
need the real stack are tagged `live` and run only with `LOCALMIND_LIVE_TESTS=true`.

## Keeping these docs current

A change that alters behaviour updates the matching page in the same commit:

| You changed... | Update |
| --- | --- |
| A controller, DTO or status code | The feature page's *HTTP API* section and [api.md](api.md) |
| An entity or column | The feature page's *Domain model* and [data-model.md](data-model.md) |
| A `*Properties` record or `application.yml` | The feature page's *Configuration* and [configuration.md](configuration.md) |
| An exception or user-facing message | The feature page's *Errors* table |
| An advisor | [chat.md](features/chat.md#prompt-advisors) and [PROMPT_GENERATION_PROCESS.md](../PROMPT_GENERATION_PROCESS.md) |
| A tool | [tools.md](features/tools.md) |
| A new feature package | A new page under `features/` with the same sections, plus this index and [architecture.md](architecture.md) |
