# Conversation (`com.localmind.conversation`)

Owns conversations (chat threads) and their persisted messages: CRUD over HTTP, the tenant-isolation check every
conversation-scoped operation runs first (`OwnershipGuard`), and two background LLM jobs that maintain conversation
metadata without overwriting concurrent user edits: automatic titling (`AutoTitleService`) and the rolling summary of
history that no longer fits the context window (`ConversationSummaryService`). Streaming a turn and assembling the
prompt belong to the `chat` feature; see [PROMPT_GENERATION_PROCESS.md](../../PROMPT_GENERATION_PROCESS.md) for how
history and summary enter the prompt.

## Responsibilities

- Create, list, update (title, pinned, model) and delete conversations of the authenticated user.
- Return a conversation's messages in chronological order.
- Enforce conversation ownership (`OwnershipGuard`) for this feature, `chat` and `attachment`.
- Cascade a conversation delete to its messages, attachment rows, stored files and Qdrant document chunks.
- Replace the `New Chat` placeholder title with a model-generated title after a completed turn.
- Fold history turns trimmed from the prompt into `conversations.summary`, tracked by the `summarized_through`
  watermark.
- Provide the targeted `@Modifying` updates (`touch`, `replacePlaceholderTitle`, `updateSummary`) used by code that runs
  outside the request transaction.
- Strip `<think>...</think>` blocks from one-shot completions (`ThinkTags`).

## Package layout

| Class | Layer | Role |
| --- | --- | --- |
| [`ConversationController`](../../../backend/src/main/java/com/localmind/conversation/controller/ConversationController.java) | controller | `/api/conversations` endpoints |
| [`ConversationService`](../../../backend/src/main/java/com/localmind/conversation/service/ConversationService.java) | service | CRUD, message listing, delete cascade |
| [`OwnershipGuard`](../../../backend/src/main/java/com/localmind/conversation/service/OwnershipGuard.java) | service (`@Component`) | Throws `AccessDeniedException` unless the conversation belongs to the user |
| [`AutoTitleService`](../../../backend/src/main/java/com/localmind/conversation/service/AutoTitleService.java) | service | `@Async` title generation from a prompt |
| [`ConversationSummaryService`](../../../backend/src/main/java/com/localmind/conversation/service/ConversationSummaryService.java) | service | `@Async` rolling summary of dropped history turns |
| [`ThinkTags`](../../../backend/src/main/java/com/localmind/conversation/service/ThinkTags.java) | service (package-private helper) | Removes `<think>` blocks (complete and unbalanced) and trims |
| [`ConversationRepository`](../../../backend/src/main/java/com/localmind/conversation/repository/ConversationRepository.java) | repository | Conversations; targeted `UPDATE` queries |
| [`ChatMessageRepository`](../../../backend/src/main/java/com/localmind/conversation/repository/ChatMessageRepository.java) | repository | Messages ordered by `createdAt`; bulk delete |
| [`Conversation`](../../../backend/src/main/java/com/localmind/conversation/entity/Conversation.java) | entity | Table `conversations` |
| [`ChatMessage`](../../../backend/src/main/java/com/localmind/conversation/entity/ChatMessage.java) | entity | Table `messages` |
| [`CreateConversationRequest`](../../../backend/src/main/java/com/localmind/conversation/dto/CreateConversationRequest.java) | dto | `POST` body |
| [`UpdateConversationRequest`](../../../backend/src/main/java/com/localmind/conversation/dto/UpdateConversationRequest.java) | dto | `PATCH` body |
| [`ConversationResponse`](../../../backend/src/main/java/com/localmind/conversation/dto/ConversationResponse.java) | dto | Conversation view |
| [`MessageResponse`](../../../backend/src/main/java/com/localmind/conversation/dto/MessageResponse.java) | dto | Message view |

## HTTP API

All endpoints require a Bearer JWT; the user id comes from `@CurrentUserId`.

| Method | Path | Request body | Response | Status | Notes |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/conversations` | `CreateConversationRequest` (optional) | `ConversationResponse` | `201` | Body may be omitted |
| `GET` | `/api/conversations` | none | `ConversationResponse[]` | `200` | Ordered by `updatedAt` desc, not paginated |
| `PATCH` | `/api/conversations/{id}` | `UpdateConversationRequest` | `ConversationResponse` | `200` | Ownership checked; partial update |
| `DELETE` | `/api/conversations/{id}` | none | none | `204` | Ownership checked; full cascade |
| `GET` | `/api/conversations/{id}/messages` | none | `MessageResponse[]` | `200` | Ownership checked; `createdAt` asc, not paginated |

Endpoint notes:

- **POST**: `@RequestBody(required = false)`. Title: trimmed `title` if non-blank, else `"New Chat"`
  (`Conversation.DEFAULT_TITLE`). Model: trimmed `modelName` if non-blank, else `localmind.llm.default-model`
  (`LlmProperties.resolveModel`). The model name is not checked against the Ollama catalogue; `ConversationService`
  does not depend on `ModelCatalogService`.
- **GET list**: only the caller's conversations (`findByUserIdOrderByUpdatedAtDesc`). Pinned conversations are not
  sorted first by the server. No pagination or limit.
- **PATCH**: each field is optional. `title` non-null: blank → 400, otherwise trimmed and set. `pinned` non-null: set.
  `modelName` non-null and non-blank: trimmed and set; blank is ignored. Saved with `saveAndFlush`, so `@PreUpdate`
  bumps `updatedAt` and the response carries the new value.
- **Ownership**: an unknown id and another user's id both return `403` (the guard runs before any lookup), never `404`.
- **GET messages**: the full history, no pagination or cursor; ties on equal `createdAt` have no secondary sort key.
- Validation errors (400) carry `message` = `"<field>: <annotation message>"`, joined with `"; "` for several fields.

`CreateConversationRequest`:

```json
{ "title": "Trip planning", "modelName": "gemma3:1b" }
```

`UpdateConversationRequest`:

```json
{ "title": "Paris trip", "pinned": true, "modelName": "qwen2.5:7b" }
```

`ConversationResponse`:

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "title": "New Chat",
  "modelName": "gemma3:1b",
  "pinned": false,
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:00Z"
}
```

`MessageResponse`:

```json
{
  "id": "1c6b1c4e-6a35-4b3e-9a8f-7d5e2a1b0c9d",
  "conversationId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "role": "ASSISTANT",
  "content": "Paris in spring is ...",
  "reasoningContent": "The user asks about ...",
  "toolCalls": "[{\"name\":\"webSearch\", ...}]",
  "tokenCount": 182,
  "createdAt": "2026-01-01T10:00:05Z"
}
```

`role` is the enum name (`USER`/`ASSISTANT`); `reasoningContent` and `toolCalls` may be `null`; `toolCalls` is the raw
JSON column returned as a string.

## Domain model

No JPA relations or foreign keys are mapped: `messages.conversation_id` and `conversations.user_id` are plain string
columns, and the cascade is done in `ConversationService`. Indexes: `idx_conversations_user_updated`
(`user_id, updated_at`) and `idx_messages_conversation_created` (`conversation_id, created_at`). The schema is
generated by Hibernate (`spring.jpa.hibernate.ddl-auto: ${JPA_DDL_AUTO:update}`), which also adds missing indexes.

### `conversations` (`Conversation`)

| Column | Type | Constraints | Default |
| --- | --- | --- | --- |
| `id` | `VARCHAR(36)` | PK, not null | `@PrePersist`: random UUID if null |
| `user_id` | `VARCHAR(36)` | not null | |
| `title` | `VARCHAR(255)` | nullable | service sets `"New Chat"` |
| `model_name` | `VARCHAR(100)` | nullable | service sets the resolved model |
| `summary` | `TEXT` | nullable | |
| `summarized_through` | timestamp (`Instant`) | nullable | creation time of the newest message folded into `summary` |
| `pinned` | boolean | not null | `false` |
| `created_at` | timestamp | not updatable | `@PrePersist`: now if null |
| `updated_at` | timestamp | | `@PrePersist`: now if null; `@PreUpdate`: now |

`DEFAULT_TITLE = "New Chat"`. `hasSummary()` is true when `summary` is non-null and non-blank.

### `messages` (`ChatMessage`)

| Column | Type | Constraints | Default |
| --- | --- | --- | --- |
| `id` | `VARCHAR(36)` | PK, not null | `@PrePersist`: random UUID if null |
| `conversation_id` | `VARCHAR(36)` | not null | |
| `role` | enum as string (`USER`, `ASSISTANT`) | not null | |
| `content` | `LONGTEXT` | not null | |
| `reasoning_content` | `LONGTEXT` | nullable | |
| `tool_calls` | `JSON` | nullable | |
| `token_count` | integer | | `@Builder.Default` `0` |
| `created_at` | timestamp | not updatable | `@PrePersist`: now if null |

Messages are written by the `chat` feature (`ChatOrchestratorService`); this feature only reads and deletes them.

### DTOs

| Record | Fields | Validation |
| --- | --- | --- |
| `CreateConversationRequest` | `String title`, `String modelName` | `title` `@Size(max = 255, message = "Title cannot exceed 255 characters")`; `modelName` `@Size(max = 100, message = "Model name cannot exceed 100 characters")` |
| `UpdateConversationRequest` | `String title`, `Boolean pinned`, `String modelName` | same `@Size` rules as above |
| `ConversationResponse` | `id`, `userId`, `title`, `modelName`, `boolean pinned`, `Instant createdAt`, `Instant updatedAt` | `from(Conversation)` |
| `MessageResponse` | `id`, `conversationId`, `role`, `content`, `reasoningContent`, `toolCalls`, `Integer tokenCount`, `Instant createdAt` | `from(ChatMessage)`; `role` = `Role.name()` or `null` |

## Business rules

1. **Ownership**: `OwnershipGuard.validateConversationOwnership(conversationId, userId)` throws `AccessDeniedException`
   if either argument is `null` or `existsByIdAndUserId` is false. Called by `updateConversation`,
   `deleteConversation`, `getConversationMessages`, `ChatOrchestratorService.streamChat` and every
   conversation-scoped `AttachmentService` method. `createConversation` and `listConversations` are scoped by
   `userId` directly.
2. **Create defaults**: blank/absent title → `"New Chat"`; blank/absent model → `localmind.llm.default-model`.
3. **Update**: blank `title` → `BadRequestException`; blank `modelName` ignored; `pinned` only changed when sent.
4. **Ordering**: conversations by `updatedAt` desc; messages by `createdAt` asc (both the HTTP listing and the chat
   history loaded by `HistoryAdvisor`). No pagination anywhere in this feature.
5. **`updatedAt` changes** on: `PATCH` (`@PreUpdate`), a completed chat turn (`touch`), a successful auto-title
   (`replacePlaceholderTitle`). It does not change on a summary update, on a partial assistant message saved after a
   stream error/disconnect, or on attachment changes.
6. **Delete cascade** (one `@Transactional` method, in this order):
   1. `AttachmentService.deleteConversationAttachments(conversationId)`: loads the attachments, bulk-deletes the rows,
      and registers an after-commit hook that deletes each stored file and the conversation's document chunks in
      Qdrant (once per distinct attachment owner).
   2. `ChatMessageRepository.deleteByConversationId` (JPQL bulk `DELETE`).
   3. `ConversationRepository.deleteById`.

   Long-term memories (`memory` feature) are user-scoped and are not deleted.
7. **Auto-title trigger**: `ChatOrchestratorService` calls `generateTitleAsync(conversationId, request.message(), model)`
   (parameters `conversationId`, `prompt`, `modelName`) after every successfully completed and persisted turn (after
   `touch`), not after errors or client disconnects. `prompt` is the user message of that turn; since the job only acts
   while the title is still the placeholder, in practice the first answered prompt whose titling succeeds names the
   conversation.
8. **Auto-title rules** (`AutoTitleService`):
   - Skips when `conversationId` or the prompt is null/blank, the conversation no longer exists, or the title is not a
     placeholder (null, blank, or equal to `"New Chat"` ignoring case).
   - Model: `LlmProperties.resolveModel(modelName)` (the turn's model, else the default).
   - Prompt: a single user message via `ChatClient.create(chatModel)` (no advisors, no tools) with Ollama option
     `model`, text = the instruction below followed by the trimmed prompt cut to 500 characters:

     ```text
     Summarize the following user prompt into a concise 3 to 5 word title. Do not include quotes, markdown, prefixes or punctuation. Reply with ONLY the title:
     ```

   - Cleaning (`cleanTitle`): `ThinkTags.strip`, remove the characters ``" \n \r # * ` _``, trim, remove leading
     and trailing runs of straight (`'`) and curly (`‘` `’`) single quotes, trim, drop a leading `title:` prefix
     (case-insensitive), cut to 50 characters, trim. Apostrophes inside words are kept (`Don't Panic Guide`). A blank
     result is discarded.
   - Write: `replacePlaceholderTitle(id, title, "New Chat", now)`, which only matches while the stored title is still
     null, blank (`TRIM(title) = ''`) or `"New Chat"` ignoring case, so a rename made while the model was generating
     wins.
9. **Summary trigger**: `HistoryAdvisor` (order 400, `chat/prompt`) trims history to the model profile's history
   budget on every chat request and passes the dropped turns (oldest first) to `summarizeDroppedTurnsAsync`. This
   happens while the prompt is built, before the model streams.
10. **Summary rules** (`ConversationSummaryService`):
    - Skips when `conversationId` is null, the turn list is null/empty, or the conversation no longer exists.
    - Keeps only turns with `createdAt` after `summarizedThrough` (all turns when the watermark is null; turns with a
      null `createdAt` are always kept). Nothing left → no model call.
    - Prompt: one `Prompt` with Ollama option `model = resolveModel(modelName)`, text =

      ```text
      Progressively summarize the conversation. Include key facts and intent concisely (2-3 sentences max). Reply ONLY with the updated summary:
      Existing summary: <summary>        (only when hasSummary(), followed by a blank line)
      New conversation turns to incorporate:
      USER: <content>
      ASSISTANT: <content>
      ```

    - A null/blank reply is discarded. Otherwise `ThinkTags.strip` is applied; the result is not stored when it is
      blank or when the last new turn has no `createdAt` (no watermark). Else
      `updateSummary(id, summary, newTurns.getLast().getCreatedAt())` stores it and advances the watermark, but only if
      the stored `summarizedThrough` is null or older than that time; a slower job that started earlier updates 0 rows
      and cannot overwrite a newer summary or move the watermark backwards.
    - `HistoryAdvisor` later injects the stored summary as a system message `<summary>\n...\n</summary>`.
11. **`ThinkTags.strip`**: removes every complete `<think>...</think>` block (regex `(?s)<think>.*?</think>`, non-greedy,
    multi-line); then, if a lone `</think>` remains (model omitted the opening tag), drops everything up to and including
    the last one; then, if a lone `<think>` remains (reply cut off before the closing tag), drops it and everything
    after it; finally trims. `<think>never closed` therefore becomes `""`. Used only by the two background services; streamed
    replies are parsed by `chat`'s `StreamingTagParser` instead.

## Side effects & async work

| Mechanism | What |
| --- | --- |
| After commit (`TransactionHooks.afterCommit`, registered by `AttachmentService.deleteConversationAttachments`) | Delete stored attachment files; `DocumentEtlService.deleteIndexedChunks(userId, conversationId)` → Qdrant delete with `VectorFilters.documentChunks` (`userId` = u AND `conversationId` = c AND `type` = `DOC_CHUNK`). Failures are logged, not rethrown. Skipped entirely when the conversation has no attachments. |
| On rollback | Nothing registered by this feature: the after-commit hooks above simply do not run. |
| `@Async AutoTitleService.generateTitleAsync` | Default Spring Boot task executor (`@EnableAsync` on `LocalMindApplication`; `spring.threads.virtual.enabled: true`, so virtual threads). One Ollama call. |
| `@Async ConversationSummaryService.summarizeDroppedTurnsAsync` | Same executor. One Ollama call. |
| `ConversationRepository.touch(id, now)` | `UPDATE ... SET updatedAt = :now`; called by `ChatOrchestratorService` at the end of a stream. |
| `ConversationRepository.replacePlaceholderTitle(id, title, placeholder, now)` | `UPDATE ... SET title, updatedAt WHERE id = :id AND (title IS NULL OR TRIM(title) = '' OR LOWER(title) = LOWER(:placeholder))`; returns rows updated. |
| `ConversationRepository.updateSummary(id, summary, through)` | `UPDATE ... SET summary, summarizedThrough WHERE id = :id AND (summarizedThrough IS NULL OR summarizedThrough < :through)`; returns rows updated (`int`), does not touch `updatedAt`. |
| `ChatMessageRepository.deleteByConversationId` | JPQL bulk `DELETE`. |

The three `ConversationRepository` updates are `@Transactional @Modifying`, so they work from threads without a
surrounding transaction. JPQL updates bypass `@PreUpdate`, which is why `now` is passed explicitly. Both background
services catch every exception, log a warning with the conversation id, and leave the row unchanged.

External HTTP: only the Ollama chat calls through Spring AI's `ChatModel`. No Qdrant reads or writes besides the
after-commit delete above.

## Configuration

This feature owns no `*Properties` record. It reads `LlmProperties` (owned by `llm`):

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `localmind.llm.default-model` | `OLLAMA_CHAT_MODEL` | `gemma3:1b` | Model of a new conversation without `modelName`; model for titling/summary when none is passed |

## Errors

| Situation | Exception | HTTP status | Client message |
| --- | --- | --- | --- |
| Conversation missing or owned by another user (PATCH, DELETE, GET messages, chat, attachments) | `AccessDeniedException` (`"Access denied: Conversation does not belong to user"`, logged only) | 403 | `You do not have access to this resource` |
| PATCH with blank `title` | `BadRequestException` | 400 | `Title must not be blank` |
| Conversation vanished between guard and load (PATCH) | `ResourceNotFoundException` | 404 | `Conversation not found` |
| `title` > 255 characters (POST/PATCH) | `MethodArgumentNotValidException` | 400 | `title: Title cannot exceed 255 characters` |
| `modelName` > 100 characters (POST/PATCH) | `MethodArgumentNotValidException` | 400 | `modelName: Model name cannot exceed 100 characters` |
| Missing/invalid JWT | security filter chain | 401 | written by `ProblemDetailSecurityHandler` |
| Auto-title or summary failure | caught and logged | none | none (row unchanged) |

## Interactions with other features

| Direction | Feature | Usage |
| --- | --- | --- |
| Calls | `attachment` | `AttachmentService.deleteConversationAttachments` on delete |
| Calls | `llm` | `LlmProperties.resolveModel` (create, titling, summary); no dependency on `ModelCatalogService` |
| Calls | Spring AI | `ChatModel` (Ollama) for titles and summaries |
| Called by | `chat` | `ChatOrchestratorService`: `OwnershipGuard`, `ConversationRepository.findById`/`touch`, `ChatMessageRepository.save`, `AutoTitleService.generateTitleAsync`. `HistoryAdvisor`: `ChatMessageRepository.findByConversationIdOrderByCreatedAtAsc` / `findByConversationIdAndIdNotOrderByCreatedAtAsc`, `ConversationRepository.findById` (summary), `ConversationSummaryService.summarizeDroppedTurnsAsync` |
| Called by | `attachment` | `OwnershipGuard` on every conversation-scoped attachment operation |

## Tests

| Test class | What it covers |
| --- | --- |
| [`ConversationControllerTest`](../../../backend/src/test/java/com/localmind/conversation/controller/ConversationControllerTest.java) | `@SpringBootTest` + MockMvc on H2: create (201), user-scoped list, PATCH, message order, delete removes conversation and messages, 403 for another user's GET messages/PATCH/DELETE |
| [`AutoTitleServiceTest`](../../../backend/src/test/java/com/localmind/conversation/service/AutoTitleServiceTest.java) | Quote and `Title:` stripping, `<think>` stripping, no update when the title is customised; `cleanTitle` keeps apostrophes inside words and drops wrapping straight, double and curly quotes and Markdown bold |
| [`ConversationSummaryServiceTest`](../../../backend/src/test/java/com/localmind/conversation/service/ConversationSummaryServiceTest.java) | `updateSummary` with the newest turn's `createdAt`; turns at/before the watermark skip the model call; no `updateSummary` when the last turn has no `createdAt` |
| [`ThinkTagsTest`](../../../backend/src/test/java/com/localmind/conversation/service/ThinkTagsTest.java) | Complete blocks (one or several), reasoning before a lone `</think>`, text after an unclosed `<think>`, a reply that is only an unclosed block, plain text unchanged |
| [`RepositoryPersistenceTests`](../../../backend/src/test/java/com/localmind/integration/RepositoryPersistenceTests.java) | Conversation/message persistence, enum mapping, JSON `tool_calls`; `updateSummary` stores a newer summary (1 row) and refuses an older one (0 rows, summary and watermark unchanged) |
| [`HistoryAdvisorTest`](../../../backend/src/test/java/com/localmind/chat/prompt/HistoryAdvisorTest.java) | Summary injection and dropped turns sent to `ConversationSummaryService` (chat side) |
| [`ChatOrchestratorServiceTest`](../../../backend/src/test/java/com/localmind/chat/service/ChatOrchestratorServiceTest.java) | `touch` and auto-title trigger at the end of a stream (chat side) |

Not covered: `ConversationService` unit tests, the attachment/file/vector part of the delete cascade, `PATCH` with a
blank title, `replacePlaceholderTitle`/`touch` queries against a database.
