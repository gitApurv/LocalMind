# Flow: chat streaming

What happens from the moment a user sends a message until the answer is stored. This page connects the features
involved; rule-level detail lives on the feature pages:

- [chat.md](../features/chat.md): endpoint, SSE wire format, turn lifecycle, tool-loop guard, `ToolSelector`
- [PROMPT_GENERATION_PROCESS.md](../../PROMPT_GENERATION_PROCESS.md): every prompt advisor, step by step
- [tools.md](../features/tools.md): what each tool does
- [conversation.md](../features/conversation.md): auto titles and rolling summaries

## Sequence

```mermaid
sequenceDiagram
    participant B as Browser
    participant C as ChatController
    participant O as ChatOrchestratorService
    participant DB as MySQL
    participant A as Prompt advisors
    participant Q as Qdrant
    participant L as Ollama
    participant T as Tools
    participant BG as @Async jobs

    B->>C: POST /api/chat/stream {conversationId, message, model?, toolsEnabled?, systemPrompt?}
    C->>O: streamChat(userId, request)
    O->>DB: OwnershipGuard, load conversation
    O->>O: resolve model + ModelProfile
    O->>DB: save USER message
    O-->>B: 200 text/event-stream (Flux)
    Note over O: Everything below runs on subscription;<br/>failures become an ERROR event
    O->>O: ToolSelector picks tools (if model supports them)
    O->>A: build prompt
    A->>DB: custom instructions, pinned memories, history, summary
    A->>Q: relevant memories, document chunks
    A-->>BG: summarise turns trimmed from history
    A->>L: stream chat completion
    loop streamed chunks
        L-->>O: chunk
        O-->>B: CONTENT / REASONING
        opt model calls a tool (max 5 per turn)
            O-->>B: TOOL_START
            O->>T: call with userId, conversationId in tool context
            T-->>O: result string
            O-->>B: TOOL_END
        end
    end
    O->>DB: save ASSISTANT message (content, reasoning, tool calls, token count)
    O->>DB: touch conversation.updated_at
    O-->>B: DONE {messageId}
    O-->>BG: generate title if still "New Chat"
```

## Phases

| Phase | Where it fails | How it fails |
| --- | --- | --- |
| Ownership, lookup, validation, saving the user message | Before the stream exists | `ProblemDetail` 400 / 401 / 403 / 404 |
| Tool selection, building the request | Start of the stream | `ERROR` "Failed to start the response. Please try again." |
| Prompt advisors | Start of the stream | Never fail the turn: a failing advisor is logged and skipped |
| Streaming | During the stream | `ERROR` with a fixed message (model unreachable, or model does not support tools); partial answer saved |
| Saving the answer | End of the stream | `ERROR` "The response could not be saved. Please try again." instead of `DONE` |
| Client disconnects | Any time | Ollama subscription cancelled; partial answer saved |

Exact strings and edge cases (blank partial answers, tool-only turns) are listed in
[chat.md — Errors](../features/chat.md#errors).

The user's message is saved before the stream starts and is kept when the turn fails, so the question stays in the
thread. A failed, stopped or interrupted turn therefore leaves it without a reply. `HistoryTrimmer` leaves such
unanswered messages out of later prompts. After a failed turn the frontend keeps its ⚠️ bubble instead of reloading
the thread from the server, which would show the question with no reply and no error.

## Prompt layout

The advisors run in a fixed order and each adds one part of the prompt:

| Order | Advisor | Adds | Switch |
| --- | --- | --- | --- |
| 100 | `PromptStructureAdvisor` | System prompt: base rules, request `systemPrompt`, custom instructions, pinned memories | — |
| 200 | `MemoryRetrievalAdvisor` | Memories semantically related to the message | `localmind.prompt.memory.semantic` |
| 300 | `AutoRagAdvisor` | Excerpts from the conversation's documents | `localmind.prompt.rag.auto` |
| 400 | `HistoryAdvisor` | Earlier turns trimmed to the model's token budget, plus the rolling summary | `localmind.prompt.history.token-trim` |
| 500 | `PromptStatsAdvisor` | Nothing; logs token counts per role | `localmind.prompt.stats.enabled` |

The token budget comes from the model's `ModelProfile` ([llm.md](../features/llm.md)): context window
(`numCtx`), reserved output (`outputReserve`), and the token budgets for document excerpts (`ragBudget`) and
history (`historyBudget`).

## After the turn

- **Title.** `AutoTitleService` (`@Async`) asks the model for a short title while the conversation still has the
  placeholder title, and writes it with a targeted `UPDATE ... WHERE title is placeholder`, so a rename by the user is
  never overwritten.
- **Summary.** When `HistoryAdvisor` drops turns to fit the budget, `ConversationSummaryService` (`@Async`) folds them
  into `conversations.summary` and advances `summarized_through`, so later prompts keep the gist of old turns.
- **Memories.** If the model called `saveUserMemory`, the fact is already stored in `user_memories` and Qdrant and
  appears in the [memory vault](../features/memory.md).
