# Memory (`com.localmind.memory`)

Owns the long-term facts the assistant stores about a user across conversations (`user_memories` rows plus one
`USER_MEMORY` embedding each in Qdrant). The feature exposes the "memory vault" API that lets the user list, pin and
delete those facts. Memories are **created only by the LLM** through the `saveUserMemory` tool in
[`UserMemoryTools`](../../../backend/src/main/java/com/localmind/tools/service/UserMemoryTools.java) (see
[tools.md](tools.md)); this feature has no create endpoint. Pinned memories are injected into every system prompt, and
non-pinned ones are retrieved semantically by the prompt pipeline (see
[PROMPT_GENERATION_PROCESS.md](../../PROMPT_GENERATION_PROCESS.md)).

## Responsibilities

- Persist memory facts per user (`UserMemory` entity, `UserMemoryRepository`).
- `GET /api/memories`: list the caller's memories, newest first.
- `PATCH /api/memories/{id}`: pin or unpin a memory.
- `DELETE /api/memories/{id}`: delete the row and, after commit, its embedding in Qdrant.
- Provide the repository queries used by the tool (`existsByUserIdAndMemoryFact`, `findByUserIdOrderByCreatedAtDesc`)
  and the prompt advisors
  (`findByUserIdAndPinnedTrue`).

## Package layout

| Class | Layer | Role |
| --- | --- | --- |
| [`MemoryController`](../../../backend/src/main/java/com/localmind/memory/controller/MemoryController.java) | controller | `/api/memories` list, pin/unpin, delete |
| [`MemoryService`](../../../backend/src/main/java/com/localmind/memory/service/MemoryService.java) | service | Lookup with ownership check (403 for unknown or another user's), pin update, delete + after-commit vector delete |
| [`UserMemoryRepository`](../../../backend/src/main/java/com/localmind/memory/repository/UserMemoryRepository.java) | repository | Derived queries scoped by `userId`; `findById` for the vault endpoints |
| [`UserMemory`](../../../backend/src/main/java/com/localmind/memory/entity/UserMemory.java) | entity | `user_memories` row |
| [`MemoryResponse`](../../../backend/src/main/java/com/localmind/memory/dto/MemoryResponse.java) | dto | Response record with `from(UserMemory)` |
| [`UpdateMemoryRequest`](../../../backend/src/main/java/com/localmind/memory/dto/UpdateMemoryRequest.java) | dto | PATCH body (`pinned`) |

## HTTP API

All endpoints require a Bearer access token; the caller is read with `@CurrentUserId`.

| Method | Path | Request body | Response | Status | Notes |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/memories` | none | `MemoryResponse[]` | `200` | Only the caller's memories, ordered by `createdAt` descending; no pagination |
| `PATCH` | `/api/memories/{id}` | `UpdateMemoryRequest` | `MemoryResponse` | `200` | `pinned` is required; only the pin flag can be changed |
| `DELETE` | `/api/memories/{id}` | none | empty | `204` | Deletes the row; embedding removed after commit |

- Ownership: the memory is loaded with `findById(id)`. An unknown id and another user's memory are both `403`
  (`AccessDeniedException`, `"Access denied: Memory does not belong to user"`, logged only), like conversations, so
  memory ids cannot be probed.
- The memory text (`memoryFact`) cannot be edited through the API.

`UpdateMemoryRequest`:

```json
{ "pinned": true }
```

`MemoryResponse` (`pinned` is a primitive `boolean`; a `NULL` column is returned as `false`):

```json
{
  "id": "3f1c2a8e-6b0d-4d55-9a57-2c1e0f9b7d10",
  "memoryFact": "Prefers dark mode",
  "pinned": false,
  "createdAt": "2026-11-16T09:12:44.512Z"
}
```

## Domain model

### `UserMemory` → table `user_memories`

| Column | Java type | Constraints | Default |
| --- | --- | --- | --- |
| `id` | `String` | PK, `length = 36`, not null | `@PrePersist`: random UUID if null (the tool sets it explicitly) |
| `user_id` | `String` | `length = 36`, not null | none |
| `memory_fact` | `String` | `TEXT`, not null | none |
| `pinned` | `Boolean` | nullable | `false` (`@Builder.Default`) |
| `created_at` | `Instant` | `updatable = false` | `@PrePersist`: `Instant.now()` if null |

- `pinned` stays a nullable `Boolean` (unlike `conversations.pinned`): the column is not declared `NOT NULL`, and
  `ddl-auto: update` never tightens an existing column, so databases created earlier may hold `NULL`, which a primitive
  `boolean` could not load. New rows always get `false` from `@Builder.Default`; `MemoryResponse` maps `NULL` to `false`.
- No JPA relations: `user_id` is a plain column (no foreign key to `users`).
- Index `idx_user_memories_user_created` (`user_id, created_at`) serves the vault list and the pinned-memory lookup.

### Records

| Record | Fields | Validation |
| --- | --- | --- |
| `UpdateMemoryRequest` | `Boolean pinned` | `@NotNull(message = "Pinned flag is required")` |
| `MemoryResponse` | `String id`, `String memoryFact`, `boolean pinned`, `Instant createdAt` | none; `from(UserMemory)` maps `pinned` with `Boolean.TRUE.equals` |

### Repository queries

| Method | Used by |
| --- | --- |
| `findByUserIdOrderByCreatedAtDesc(userId)` | `MemoryService.listMemories`, `UserMemoryTools.recallUserMemories` (fallback, first 20 used) |
| `existsByUserIdAndMemoryFact(userId, memoryFact)` | `UserMemoryTools.saveUserMemory` (exact-duplicate check) |
| `findByUserIdAndPinnedTrue(userId)` | `PromptStructureAdvisor` (pinned block), `MemoryRetrievalAdvisor` (dedup of pinned facts) |
| `findById(id)` (inherited) | `MemoryService` (lookup, then ownership check against `userId`) |

## Business rules

1. Every read and write is scoped to the caller's `userId`: the list is filtered by `userId`; `PATCH`/`DELETE` load by
   id and reject an unknown id or another user's memory with `403`.
2. List order is `createdAt` descending.
3. `PATCH` sets `pinned` to the request value and saves; nothing else changes.
4. `DELETE` removes the row in the request transaction, then removes the embedding with filter
   `VectorFilters.userMemory(userId, memoryId)` after commit.
5. Creation (by the `saveUserMemory` tool, not this feature): the fact is trimmed; blank facts and facts over 1000
   characters are rejected with a tool error string; if the user already has a memory with exactly the same text
   (`existsByUserIdAndMemoryFact` on the trimmed fact, compared by the database) nothing is stored and the tool returns
   `Memory already saved: <fact>`. Otherwise the row is saved with a fresh UUID, then embedded into Qdrant with metadata
   `userId`, `type = USER_MEMORY`, `memoryId`. Row and embedding are written in one transaction (`UserMemoryWriteService`): if `vectorStore.add` throws the row
   rolls back, and a rollback after the add deletes the point again via `TransactionHooks`.
6. Pinned memories are always added to the system prompt; non-pinned ones only when semantically relevant
   (see [PROMPT_GENERATION_PROCESS.md](../../PROMPT_GENERATION_PROCESS.md)).

## Side effects & async work

| Trigger | Effect |
| --- | --- |
| `MemoryService.deleteMemory` commits | `TransactionHooks.afterCommit`: `vectorStore.delete(VectorFilters.userMemory(userId, memoryId))`, i.e. `userId == u AND memoryId == m AND type == USER_MEMORY`. A failure is logged by `TransactionHooks`, never thrown; the row is already gone. |
| `MemoryService.deleteMemory` rolls back | Nothing (no external effect has happened yet). |
| Tool `saveUserMemory` (in `tools`) | `vectorStore.add` of one `Document(fact, {userId, type: USER_MEMORY, memoryId})`; in the same transaction as the row, point deleted again on rollback. |
| Tool `recallUserMemories` / `MemoryRetrievalAdvisor` (other features) | Qdrant similarity search with `VectorFilters.userMemories(userId)` (`userId == u AND type == USER_MEMORY`). |

No `@Async` methods and no `@Modifying` queries in this feature.

## Configuration

None. The feature owns no `*Properties` record. Memory retrieval in the prompt is toggled by
`localmind.prompt.memory.semantic` (owned by `chat`).

## Errors

| Situation | Exception | HTTP status | Client message |
| --- | --- | --- | --- |
| Memory id unknown or another user's (PATCH, DELETE) | `AccessDeniedException` (`"Access denied: Memory does not belong to user"`, logged only) | 403 | `You do not have access to this resource` |
| `pinned` missing or `null` in PATCH body | `MethodArgumentNotValidException` (MVC) | 400 | `pinned: Pinned flag is required` |
| Malformed JSON body | handled by `ResponseEntityExceptionHandler` | 400 | standard ProblemDetail |
| No / invalid Bearer token | security filter chain | 401 | written by `ProblemDetailSecurityHandler` |

## Interactions with other features

| Direction | Feature | Usage |
| --- | --- | --- |
| uses | `common` | `TransactionHooks`, `VectorFilters`, `ResourceNotFoundException`, `@CurrentUserId`, `VectorStore` bean; Spring Security `AccessDeniedException` |
| used by | `tools` | `UserMemoryTools` checks for duplicates (`existsByUserIdAndMemoryFact`), saves memories (`save`, through `UserMemoryWriteService`) and lists them (`findByUserIdOrderByCreatedAtDesc`) |
| used by | `chat` | `PromptStructureAdvisor` and `MemoryRetrievalAdvisor` read `findByUserIdAndPinnedTrue` |

## Tests

| Test class | What it covers |
| --- | --- |
| [`MemoryControllerTest`](../../../backend/src/test/java/com/localmind/memory/controller/MemoryControllerTest.java) | `@SpringBootTest` on H2: list returns only the caller's memories, pin via PATCH, DELETE returns 204, other user's memory and an unknown id are both 403 |
| [`MemoryServiceTest`](../../../backend/src/test/java/com/localmind/memory/service/MemoryServiceTest.java) | Unit test with mocks: `updateMemory` pins the caller's memory; `deleteMemory` deletes the row and the vector with `VectorFilters.userMemory(userId, memoryId)` (hook runs immediately without a transaction); another user's memory is `AccessDeniedException` for PATCH and DELETE and neither row nor vector is changed; an unknown id is `ResourceNotFoundException` |
| [`UserMemoryToolsTest`](../../../backend/src/test/java/com/localmind/tools/service/UserMemoryToolsTest.java) | Tool save (repository + vector index), missing user / blank / over-long fact errors, exact duplicate not stored, recall via vector search and DB fallback capped to 20 |
| [`MemoryRetrievalAdvisorTest`](../../../backend/src/test/java/com/localmind/chat/prompt/advisor/MemoryRetrievalAdvisorTest.java) | Semantic memory injection into the system prompt |
