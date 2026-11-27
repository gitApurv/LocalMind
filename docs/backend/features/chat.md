# Chat (`com.localmind.chat`)

Owns the streaming chat turn: it accepts one user message for a conversation, persists it, assembles the prompt through an ordered chain of Spring AI advisors (`chat/prompt`), decides which LLM tools the model may call, streams the Ollama output to the browser as typed Server-Sent Events, separates `<think>` reasoning from answer text, and persists the assistant reply (complete or partial). It has no tables of its own; it reads and writes the `conversation` feature's `messages` and `conversations` tables. The step-by-step prompt pipeline is documented in [PROMPT_GENERATION_PROCESS.md](../../PROMPT_GENERATION_PROCESS.md); this page covers the HTTP/SSE contract and the orchestration around it.

## Responsibilities

- Expose `POST /api/chat/stream` (`text/event-stream`).
- Check conversation ownership and resolve the model and its `ModelProfile` before the stream starts, so these failures are plain HTTP errors.
- Persist the user message before streaming; persist the assistant message on completion, error or client disconnect.
- Run the five `PromptAdvisor`s with per-turn context (`ChatContextKeys`).
- Select tools per turn (`ToolSelector`), wrap each in `MonitoredToolCallback` (SSE events, audit trail, loop guard).
- Split streamed text into `REASONING` and `CONTENT` events (`StreamingTagParser`).
- Translate stream failures into fixed `ERROR` messages; never forward exception text.
- Trigger auto-titling after a completed turn and bump `conversations.updated_at` with a targeted update.
- Provide the token estimator (`TokenCounter` / `CharacterTokenCounter`) used across the prompt pipeline.

## Package layout

| Class | Layer | Role |
| --- | --- | --- |
| [ChatController](../../../backend/src/main/java/com/localmind/chat/controller/ChatController.java) | controller | `POST /api/chat/stream`; wraps each `ChatEvent` in a `ServerSentEvent` |
| [ChatRequest](../../../backend/src/main/java/com/localmind/chat/dto/ChatRequest.java) | dto | Request body; `isToolsEnabled()` defaults to `true` |
| [ChatEvent](../../../backend/src/main/java/com/localmind/chat/dto/ChatEvent.java) | dto | SSE payload (`type`, `content`, `tool`, `messageId`), `NON_NULL` JSON |
| [PromptProperties](../../../backend/src/main/java/com/localmind/chat/config/PromptProperties.java) | config | `localmind.prompt.*` advisor switches |
| [ToolCallRecord](../../../backend/src/main/java/com/localmind/chat/model/ToolCallRecord.java) | model | One audited tool call (`tool`, `input`, `timestamp`, `resultSummary`); element of `messages.tool_calls`, written by `MonitoredToolCallback`, read by `ToolNoteRenderer` |
| [ChatOrchestratorService](../../../backend/src/main/java/com/localmind/chat/service/ChatOrchestratorService.java) | service | Pre-stream checks, model resolution, user-message persistence; creates one `ChatTurn` per stream |
| [ChatTurn](../../../backend/src/main/java/com/localmind/chat/service/ChatTurn.java) | service (package-private) | One stream: prompt assembly, tool registration, chunk relay, assistant-message persistence, error mapping |
| [StreamingTagParser](../../../backend/src/main/java/com/localmind/chat/service/StreamingTagParser.java) | service (package-private) | `<think>` splitter with chunk-boundary buffering; accumulates content/reasoning |
| [MonitoredToolCallback](../../../backend/src/main/java/com/localmind/chat/service/MonitoredToolCallback.java) | service (package-private) | `ToolCallback` decorator: TOOL_START/TOOL_END, audit record, 5-call cap |
| [PromptAdvisor](../../../backend/src/main/java/com/localmind/chat/prompt/PromptAdvisor.java) | prompt | `BaseAdvisor` with no-op `after` and context helpers (`contextString`, `modelProfile`, `userText`) |
| [PromptStructureAdvisor](../../../backend/src/main/java/com/localmind/chat/prompt/advisor/PromptStructureAdvisor.java) | prompt/advisor | Order 100: system message |
| [MemoryRetrievalAdvisor](../../../backend/src/main/java/com/localmind/chat/prompt/advisor/MemoryRetrievalAdvisor.java) | prompt/advisor | Order 200: semantic memories |
| [AutoRagAdvisor](../../../backend/src/main/java/com/localmind/chat/prompt/advisor/AutoRagAdvisor.java) | prompt/advisor | Order 300: document excerpts |
| [HistoryAdvisor](../../../backend/src/main/java/com/localmind/chat/prompt/advisor/HistoryAdvisor.java) | prompt/advisor | Order 400: summary + trimmed history |
| [PromptStatsAdvisor](../../../backend/src/main/java/com/localmind/chat/prompt/advisor/PromptStatsAdvisor.java) | prompt/advisor | Order 500: token log |
| [HistoryTrimmer](../../../backend/src/main/java/com/localmind/chat/prompt/history/HistoryTrimmer.java) | prompt/history | Fits history into a token budget, newest first, keeping USER/ASSISTANT pairs; skips unanswered USER messages |
| [TrimmedHistory](../../../backend/src/main/java/com/localmind/chat/prompt/history/TrimmedHistory.java) | prompt/history | Record: kept `messages`, `dropped` turns |
| [ToolNoteRenderer](../../../backend/src/main/java/com/localmind/chat/prompt/history/ToolNoteRenderer.java) | prompt/history | Renders stored `tool_calls` JSON as `[used tools: ...]` for history replay |
| [ToolSelector](../../../backend/src/main/java/com/localmind/chat/service/ToolSelector.java) | service | Chooses the tool callbacks offered for one turn |
| [TokenCounter](../../../backend/src/main/java/com/localmind/chat/prompt/token/TokenCounter.java) | prompt/token | `int estimate(String)` |
| [CharacterTokenCounter](../../../backend/src/main/java/com/localmind/chat/prompt/token/CharacterTokenCounter.java) | prompt/token | The only `TokenCounter` bean: `length / 4` |

## HTTP API

| Method | Path | Request body | Response | Status | Notes |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/chat/stream` | `ChatRequest` | `text/event-stream` of `ChatEvent` | `200` | Bearer JWT required; caller from `@CurrentUserId` |

Notes:

- Ownership (`OwnershipGuard`), conversation lookup, model/profile resolution and user-message persistence run synchronously in `streamChat` before the `Flux` is returned, so they fail as ProblemDetail responses (`400`/`401`/`403`), not as SSE events. Everything else (advisors, tool selection, Ollama) runs when Spring MVC subscribes and fails as an `ERROR` event.
- The async request timeout is 3 minutes (`common.config.WebMvcConfig`).
- `toolsEnabled` omitted or `null` means `true`. `model` blank/omitted falls back to the conversation's `modelName`, then `localmind.llm.default-model` (`LlmProperties.resolveModel`, first non-blank, trimmed).
- `systemPrompt` is appended to the system message for this turn only; it is not persisted.

Request:

```json
{
  "conversationId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "message": "What is in the attached spec?",
  "model": "qwen2.5:7b",
  "toolsEnabled": true,
  "systemPrompt": "Answer in German."
}
```

### SSE wire format

Each event is one `ServerSentEvent` with only a `data` field (no `event:`/`id:`), serialized as `ChatEvent` JSON with `null` fields omitted (`@JsonInclude(NON_NULL)`); events are separated by a blank line.

| `type` | Fields set | Emitted by | Meaning |
| --- | --- | --- | --- |
| `CONTENT` | `type`, `content` | `StreamingTagParser` | Answer text outside `<think>` (chunked as received) |
| `REASONING` | `type`, `content` | `StreamingTagParser` | Text inside `<think>...</think>` |
| `TOOL_START` | `type`, `tool` | `MonitoredToolCallback` | A tool call (within the 5-call cap) is about to run |
| `TOOL_END` | `type`, `tool` | `MonitoredToolCallback` | That call finished (always sent, also on tool exception) |
| `ERROR` | `type`, `content` | `ChatTurn` | Fixed user-facing message; the stream completes right after |
| `DONE` | `type`, `messageId` | `ChatTurn.onComplete` | Assistant message saved; `messageId` is its UUID |

```text
data:{"type":"REASONING","content":"The user wants a sum."}

data:{"type":"TOOL_START","tool":"calculateExpression"}

data:{"type":"TOOL_END","tool":"calculateExpression"}

data:{"type":"CONTENT","content":"The result is **552**."}

data:{"type":"DONE","messageId":"a812ef94-9140-4bd2-97b4-3a2135082121"}

data:{"type":"ERROR","content":"The model stopped responding. Check that Ollama is running and the model is installed, then try again."}
```

A successful stream ends with `DONE`; a failed one ends with `ERROR`. If persisting the reply fails in `onComplete`, `ERROR` is sent instead of `DONE`. Tools blocked by the loop guard emit no events.

## Domain model

No entities. The feature writes `conversation.entity.ChatMessage` (`messages`) and updates `conversations.updated_at`; see the conversation feature page.

Rows written per turn:

| When | `role` | `content` | `reasoning_content` | `tool_calls` | `token_count` |
| --- | --- | --- | --- | --- | --- |
| Before streaming | `USER` | `message.trim()` | `null` | `null` | `max(1, estimate(content))` |
| Completion / error / disconnect | `ASSISTANT` | accumulated content (may be `""`) | accumulated reasoning, `null` if blank | audit JSON array, `null` if no recorded call | `max(1, estimate(content))`: reasoning is stored for display but never replayed, so it is not counted |

`tool_calls` element: a `ToolCallRecord` (built by `MonitoredToolCallback`, serialized with Jackson 3 `ObjectMapper`; `timestamp` is an `Instant` written as ISO-8601):

```json
[{"tool": "searchWeb", "input": "{\"query\":\"spring ai numCtx\"}", "timestamp": "2026-11-16T10:15:30.123Z", "resultSummary": "Web Search Results for 'spring ai numCtx'..."}]
```

`resultSummary` is the result cut to 120 chars plus `...`, `"empty"` for a `null` result, and `"failed"` (`MonitoredToolCallback.FAILED_SUMMARY`) when the tool threw, so every recorded call has a summary. `ToolCallRecord` ignores unknown properties, so rows in an older format (e.g. with a `query` field) still render as a history note.

Records:

| Record | Fields and validation |
| --- | --- |
| `ChatRequest` | `conversationId` `@NotBlank @Size(max=36)`; `message` `@NotBlank @Size(max=32000)`; `model` `@Size(max=100)`; `toolsEnabled` `Boolean`; `systemPrompt` `@Size(max=8000)` |
| `ChatEvent` | `type` (`TOOL_START`, `TOOL_END`, `REASONING`, `CONTENT`, `ERROR`, `DONE`), `content`, `tool`, `messageId`; factories `content`, `reasoning`, `toolStart`, `toolEnd`, `error`, `done` |
| `PromptProperties` | `rag.auto`, `memory.semantic`, `history.tokenTrim`, `stats.enabled`; all `true` by default (compact constructor + `@DefaultValue`) |
| `TrimmedHistory` | `List<Message> messages` (kept, oldest first), `List<ChatMessage> dropped` (oldest first) |

## Business rules

### Turn lifecycle (`ChatOrchestratorService`, `ChatTurn`)

```mermaid
flowchart TD
    A[streamChat] --> B[OwnershipGuard.lockOwnedConversation, resolveModel, getProfile]
    B --> C[save USER message]
    C --> D[Flux.create: ChatTurn.start]
    D --> E[buildPrompt: options, advisors, ToolSelector]
    E --> F[stream().chatResponse().subscribe]
    F -->|chunk| G[StreamingTagParser -> CONTENT / REASONING]
    F -->|complete| H[flush, save ASSISTANT, touch, DONE, generateTitleAsync]
    F -->|error| I[flush, save partial, ERROR]
    D -->|client disconnect| J[dispose upstream, flush, save partial]
    E -->|exception building/subscribing| K[ERROR start failed]
```

1. `OwnershipGuard.lockOwnedConversation` checks ownership and loads the conversation in one query (`findLockedByIdAndUserId`, `PESSIMISTIC_WRITE` until the user message commits, the same lock upload and conversation delete take); a missing conversation and another user's are both `403`.
2. Model: `resolveModel(request.model, conversation.modelName)` then default; profile: `ModelProfileRegistry.getProfile(model)`.
3. `streamChat` is `@Transactional`: the ownership check, conversation load and user-message save commit when it returns, before the stream is subscribed. The user message is therefore saved before the stream exists, so it is kept even if the stream never produces output.
4. Ollama options per turn: `model`, `numCtx = profile.numCtx`, `numPredict = profile.outputReserve`. A fresh `ChatClient` is built from the injected `ChatModel` each turn; the user text is `message.trim()`, and the same trimmed text is passed to `ToolSelector` and `AutoTitleService`.
5. Advisor context: `conversationId`, `userId`, `modelProfile`, `currentMessageId` (if the saved user message has an ID), `requestSystemPrompt` (if non-null).
6. Tools: if `ToolSelector` returns none, nothing is registered (and an INFO log is written when tools were requested). Otherwise each tool is wrapped in `MonitoredToolCallback` sharing one counter and one audit list, and the tool context gets `userId` and `conversationId`.
7. Chunks: empty/`null` outputs are ignored; text goes through `StreamingTagParser.processChunk`.
8. Completion: `parser.flush`, save the assistant message, `ConversationRepository.touch(id, now)`, emit `DONE`, call `AutoTitleService.generateTitleAsync(conversationId, trimmed message, model)`, complete. Any exception in this block emits `ERROR` with the persist-failed message; the sink is always completed.
9. Model error (`onError`): log, save partial assistant message, emit `ERROR`, complete. `mentionsUnsupportedTools` checks the messages of the cause chain and suppressed exceptions; if one contains `does not support tools` (`TOOLS_UNSUPPORTED_MARKER`), the tool-unsupported message is used, otherwise the generic stream-failed message. Other errors that merely mention `400` or `tools` get the generic message.
10. Client disconnect: `sink.onDispose` disposes the Ollama subscription if it is still active and saves the partial assistant message. After normal completion or error the upstream is already disposed, so no second save happens.
11. Every partial save first calls `parser.flush`, so text held back as a possible partial tag is kept. On error the flushed text is also sent as a `CONTENT`/`REASONING` event before `ERROR`; after a disconnect the sink is already cancelled and the events are dropped. Partial saves are skipped when both accumulated content and reasoning are blank (tool audit alone is not saved); failures are logged at WARN and swallowed. A saved partial reply also bumps `updated_at` (`touch`), since the conversation gained a message; no title is generated on error or disconnect.
12. Exception while building the prompt or subscribing (`start`): log, emit `ERROR` (start-failed message), complete. No assistant message is saved.

### `StreamingTagParser`

1. One helper (`consume`) handles both modes: it looks for `<think>` outside a block and `</think>` inside one. Outside a think block, the first `<think>` switches to reasoning; text before it is `CONTENT`. Inside, the first `</think>` switches back; text before it is `REASONING`. Multiple blocks per stream are supported.
2. If a chunk ends with a proper prefix of the tag being looked for (`<`, `<th`, `</thi`, ...), that suffix is held in `pending` and prepended to the next chunk; text before it is emitted immediately.
3. `flush()` at completion and before every partial save emits any `pending` text as reasoning (if inside a block) or content. An unclosed `<think>` keeps the rest of the stream as reasoning.
4. Tags are matched case-sensitively and only literally `<think>` / `</think>`. Tags themselves are never emitted or stored.
5. Everything emitted is appended to `contentBuffer` / `thinkBuffer`, which become `content` / `reasoning_content`. `processChunk` and `flush` are `synchronized`.

### `MonitoredToolCallback` and tool-loop guard

1. A per-turn `AtomicInteger` shared by all tools is incremented on every call. Calls 1-5 run normally; from call 6 on, the delegate is not invoked, nothing is recorded or emitted, a WARN is logged and the model receives `Loop protection triggered: Maximum tool calls (5) reached. Please provide the final response to the user now.`
2. Allowed call: note the start time, emit `TOOL_START`, call `delegate.call(input, toolContext)`; in `finally`, append a `ToolCallRecord` (`tool`, `input`, start `timestamp`, `resultSummary`) to the turn's audit list and emit `TOOL_END`. Records are therefore in completion order, which equals call order because tools run one at a time.
3. Delegate exception: logged at WARN; the `ToolCallRecord` gets `resultSummary = "failed"` (`FAILED_SUMMARY`); the model receives `Error executing tool (<name>). Continue without its result.`; `TOOL_END` is still emitted.
4. Tool name is `getToolDefinition().name()`, or `unknown` if there is no definition. `call(String)` delegates with a `null` context.

### `ToolSelector`

1. Returns no tools when `toolsRequested` is false or `ModelCatalogService.supportsTools(model)` is false (catalogue lookup by exact name/model or name + `:latest`, no prefix matching; falls back to the `ModelProfileRegistry` profile, which is `false` for unknown models).
2. Always offered (in this order): `getCurrentDateTime`, `calculateExpression`, `saveUserMemory`, `recallUserMemories`, `searchWeb`.
3. `readWebPage` only if the lower-cased message contains `http://`, `https://` or `www.`.
4. `searchUploadedDocs` only if the conversation has an attachment with status `READY`.
5. `null` callbacks from a tool component are skipped.

### Prompt advisors

Spring AI runs all `PromptAdvisor` beans by `getOrder()`. Details, formats and examples: [PROMPT_GENERATION_PROCESS.md](../../PROMPT_GENERATION_PROCESS.md).

| Order | Class | Toggle | Adds |
| --- | --- | --- | --- |
| 100 | `PromptStructureAdvisor` | none (always on) | First system message: base rules, request `systemPrompt`, user custom instructions, pinned memories |
| 200 | `MemoryRetrievalAdvisor` | `localmind.prompt.memory.semantic` | `Relevant User Memories:` appended to the system message (top 5, threshold 0.4, max 200 tokens, pinned facts skipped) |
| 300 | `AutoRagAdvisor` | `localmind.prompt.rag.auto` | `<reference source="...">` blocks prepended to the user message (top 4, threshold 0.4, `ragBudget`, default 2000) when a `READY` attachment exists |
| 400 | `HistoryAdvisor` | `localmind.prompt.history.token-trim` | `<summary>` system message and history trimmed to `historyBudget` (default 3400); `false` sends no history and no summary |
| 500 | `PromptStatsAdvisor` | `localmind.prompt.stats.enabled` | Nothing; logs token totals per role and warns when total > `numCtx` (default 8192) |

Advisors never fail a turn. Each implements `apply()`; `PromptAdvisor#before` (the method Spring AI calls) wraps it, logs any `RuntimeException` at `WARN` with the advisor's name, and returns the request unchanged, so the turn continues without that advisor's contribution. This matters because Spring AI runs `before()` inside the response stream: an exception there would otherwise reach `ChatTurn.onError` and show the misleading "The model stopped responding. Check that Ollama is running…" error. Two advisors degrade more finely: `PromptStructureAdvisor` keeps the base rules and the request `systemPrompt` when custom instructions or pinned memories cannot be loaded (returning the request unchanged would drop the rule that document excerpts are data, not instructions), and `HistoryAdvisor` still sends the trimmed history when the summary job cannot be started.

`HistoryTrimmer`: first removes every `USER` message not directly followed by an `ASSISTANT` message. The user message is saved before its turn streams, so a turn that failed, was stopped before any output, or was cut off by a restart leaves it unanswered; replaying it would show the model two user turns in a row. These messages stay in the database and in the thread, and are not sent for summarising. It then walks from newest to oldest; an `ASSISTANT` directly preceded by a `USER` is taken as a pair (assistant text plus `ToolNoteRenderer` note), anything else singly. A unit that would exceed the budget stops the walk, except the first (newest) unit, which is always kept. Everything older than the stop point is `dropped`. `reasoning_content` is never replayed. `budget <= 0` drops everything.

### Token counting

`CharacterTokenCounter.estimate`: `0` for `null`/empty, otherwise `max(1, length / 4)`. It is a character heuristic, not a tokenizer. Used by `HistoryTrimmer`, `AutoRagAdvisor`, `MemoryRetrievalAdvisor`, `PromptStatsAdvisor`, `ChatOrchestratorService` (user `token_count`) and `ChatTurn` (assistant `token_count`), both floored at 1.

## Side effects & async work

| What | When | Mechanism |
| --- | --- | --- |
| Save `USER` message | Before the stream | `ChatMessageRepository.save`, no transaction |
| Save `ASSISTANT` message | Completion, model error, client disconnect | `ChatMessageRepository.save` from the stream thread |
| Bump `conversations.updated_at` | Completion and every saved partial reply | `ConversationRepository.touch` (`@Modifying` UPDATE), never saves the loaded `Conversation` |
| Auto-title | After `DONE` on every completed turn | `AutoTitleService.generateTitleAsync` (`@Async`, Spring Boot task executor on virtual threads); only replaces the `New Chat` placeholder |
| Rolling summary | During prompt assembly when turns were dropped | `ConversationSummaryService.summarizeDroppedTurnsAsync` (`@Async`) called by `HistoryAdvisor` |
| Qdrant read: memories | Order 200 | `similaritySearch`, `VectorFilters.userMemories(userId)` (`userId`, `type = USER_MEMORY`) |
| Qdrant read: document chunks | Order 300 | `similaritySearch`, `VectorFilters.documentChunks(userId, conversationId)` (`userId`, `conversationId`, `type = DOC_CHUNK`); excerpt label from `VectorMetadata.FILE_NAME` |
| Ollama chat (streaming) | Each turn | `ChatClient` over the shared Spring AI `ChatModel` |
| Tool calls | During the stream | Tools from the `tools` feature, with `ToolContext` `{userId, conversationId}` |

No `TransactionHooks` are used; the feature writes no files and no vectors. Qdrant writes come only from tools (`saveUserMemory`) owned by the `tools` feature.

## Configuration

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `localmind.prompt.rag.auto` | `PROMPT_RAG_AUTO` | `true` | Enables `AutoRagAdvisor` |
| `localmind.prompt.memory.semantic` | `PROMPT_MEMORY_SEMANTIC` | `true` | Enables `MemoryRetrievalAdvisor` |
| `localmind.prompt.history.token-trim` | `PROMPT_HISTORY_TOKEN_TRIM` | `true` | Enables `HistoryAdvisor` (history + summary) |
| `localmind.prompt.stats.enabled` | `PROMPT_STATS_ENABLED` | `true` | Enables `PromptStatsAdvisor` logging |
| `localmind.llm.default-model` (read via `LlmProperties`) | `OLLAMA_CHAT_MODEL` | `gemma3:1b` | Model when neither request nor conversation sets one |

Advisor switches are read once in each advisor's constructor. Fixed constants (not configurable): memory top-K 5, document top-K 4, similarity threshold 0.4, memory budget 200 tokens, tool cap 5, result summary 120 chars.

## Errors

| Situation | Exception | HTTP status | Client message (exact string from code) |
| --- | --- | --- | --- |
| Invalid body (`@Valid`) | `MethodArgumentNotValidException` | 400 | `<field>: <message>` joined by `"; "`, e.g. `message: Message content must not be blank` |
| No/invalid JWT | (security filter chain) | 401 | written by `ProblemDetailSecurityHandler` |
| Conversation missing or not owned | `AccessDeniedException` (`OwnershipGuard`) | 403 | `You do not have access to this resource` |
| Exception building the prompt / subscribing | SSE `ERROR` | 200 (stream) | `Failed to start the response. Please try again.` |
| Ollama stream error, tools unsupported | SSE `ERROR` | 200 (stream) | `Model '<model>' does not support tool calling in Ollama. Please disable Tools or select a tool-compatible model.` |
| Any other stream error | SSE `ERROR` | 200 (stream) | `The model stopped responding. Check that Ollama is running and the model is installed, then try again.` |
| Saving the reply / touch fails on completion | SSE `ERROR` | 200 (stream) | `The response could not be saved. Please try again.` |
| Tool throws | none (returned to the model) | n/a | `Error executing tool (<name>). Continue without its result.` |
| More than 5 tool calls | none (returned to the model) | n/a | `Loop protection triggered: Maximum tool calls (5) reached. Please provide the final response to the user now.` |

Validation messages: `Conversation ID must not be blank`, `Conversation ID cannot exceed 36 characters`, `Message content must not be blank`, `Message cannot exceed 32000 characters`, `Model name cannot exceed 100 characters`, `System prompt cannot exceed 8000 characters`.

## Interactions with other features

| Feature | Used | By |
| --- | --- | --- |
| conversation | `OwnershipGuard` (`lockOwnedConversation`), `ConversationRepository` (`touch`; `findById` in `HistoryAdvisor` for the summary), `ChatMessageRepository` (`save`, history queries), `AutoTitleService`, `ConversationSummaryService` | `ChatOrchestratorService` (ownership, user message), `ChatTurn` (assistant message, `touch`, title), `HistoryAdvisor` |
| llm | `LlmProperties.resolveModel`, `ModelProfileRegistry.getProfile`, `ModelCatalogService.supportsTools`, `ModelProfile` | `ChatOrchestratorService`, `ChatTurn`, `ToolSelector`, advisors |
| tools | `DateTimeTools`, `MathTools`, `UserMemoryTools`, `WebSearchTools`, `WebScraperTools`, `DocumentSearchTools` (`toolCallback()`) | `ToolSelector` |
| attachment | `AttachmentRepository.existsByConversationIdAndStatus(.., READY)` | `ToolSelector`, `AutoRagAdvisor` |
| memory | `UserMemoryRepository.findByUserIdAndPinnedTrue` | `PromptStructureAdvisor`, `MemoryRetrievalAdvisor` |
| user | `UserRepository.findById` (custom instructions) | `PromptStructureAdvisor` |
| common | `ChatContextKeys`, `VectorFilters`, `VectorMetadata`, `@CurrentUserId`, `ResourceNotFoundException` | throughout |

Callers: only the HTTP client. `HistoryTrimmer`, `ToolNoteRenderer` and `TokenCounter` are public beans; within `main` they are used only by this feature.

## Tests

| Test class | What it covers |
| --- | --- |
| [ChatControllerTest](../../../backend/src/test/java/com/localmind/chat/controller/ChatControllerTest.java) | `@SpringBootTest`: 401 without JWT, 400 on blank message, 403 for another user's conversation, async start when authorised |
| [ChatOrchestratorServiceTest](../../../backend/src/test/java/com/localmind/chat/service/ChatOrchestratorServiceTest.java) | Content streaming and persistence, `<think>` split across stream chunks, a turn that completes normally although advisors fail to load user data and history, ownership errors (missing or foreign conversation → 403), custom model and system prompt, history, custom instructions/memories, tools skipped for gemma3:1b, tool-unsupported error message, partial reply on stream error keeps text held back by the parser |
| [MonitoredToolCallbackTest](../../../backend/src/test/java/com/localmind/chat/service/MonitoredToolCallbackTest.java) | TOOL_START/TOOL_END events, `resultSummary` recorded, 5-call cap (blocked calls not audited); a tool that throws: the model gets the fixed `Error executing tool (searchWeb). Continue without its result.` and the call is still recorded as a `ToolCallRecord` with its name, input, timestamp and `resultSummary` `failed` |
| [StreamingTagParserTest](../../../backend/src/test/java/com/localmind/chat/service/StreamingTagParserTest.java) | `<think>`/`</think>` tags split across chunks; `flush` emits a trailing partial tag as content |
| [ToolNoteRendererTest](../../../backend/src/test/java/com/localmind/chat/prompt/history/ToolNoteRendererTest.java) | `[used tools: ...]` note from serialized `ToolCallRecord`s (ISO-8601 `timestamp`); legacy rows with unknown fields; no note for missing, empty or unreadable JSON |
| [PromptStructureAdvisorTest](../../../backend/src/test/java/com/localmind/chat/prompt/advisor/PromptStructureAdvisorTest.java) | System message section order; base rules and request system prompt kept when user data cannot be loaded |
| [MemoryRetrievalAdvisorTest](../../../backend/src/test/java/com/localmind/chat/prompt/advisor/MemoryRetrievalAdvisorTest.java) | Retrieved memories appended to the system message; request unchanged when the vector search fails |
| [AutoRagAdvisorTest](../../../backend/src/test/java/com/localmind/chat/prompt/advisor/AutoRagAdvisorTest.java) | Skip without `READY` attachments; reference excerpts injected; request unchanged when the attachment lookup fails |
| [HistoryAdvisorTest](../../../backend/src/test/java/com/localmind/chat/prompt/advisor/HistoryAdvisorTest.java) | Summary message, system-first ordering, dropped turns sent to the summary service; request unchanged when history cannot be loaded; history kept when the summary job cannot start |
| [HistoryTrimmerTest](../../../backend/src/test/java/com/localmind/chat/prompt/history/HistoryTrimmerTest.java) | USER/ASSISTANT pairs preserved while trimming; unanswered USER messages skipped and not sent for summarising |
| [PromptStatsAdvisorTest](../../../backend/src/test/java/com/localmind/chat/prompt/advisor/PromptStatsAdvisorTest.java) | Order 500 and a non-null request returned (log content not asserted); request unchanged when token counting fails |
| [TokenCounterTest](../../../backend/src/test/java/com/localmind/chat/prompt/token/TokenCounterTest.java) | `CharacterTokenCounter` estimate |
| [ToolSelectorTest](../../../backend/src/test/java/com/localmind/chat/service/ToolSelectorTest.java) | No tools for unsupported model / not requested; doc search only with attachments; web scraper only with URL |
| [PromptAssemblyIntegrationTest](../../../backend/src/test/java/com/localmind/integration/PromptAssemblyIntegrationTest.java) | Advisor ordering through a real `ChatClient`, using anonymous stand-in advisors (not the production classes) |
| [EndToEndChatIntegrationTest](../../../backend/src/test/java/com/localmind/integration/EndToEndChatIntegrationTest.java) | `@Tag("live")`: register, login, stream with gemma3:1b, persistence, history and tenant isolation |
