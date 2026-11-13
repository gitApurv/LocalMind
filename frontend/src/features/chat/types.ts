export type MessageRole = "USER" | "ASSISTANT" | "SYSTEM";

/** A chat message as the UI shows it, built from `MessageResponse` or while streaming. */
export interface Message {
  id: string;
  conversationId: string;
  role: MessageRole;
  content: string;
  reasoningContent?: string | null;
  /** Tools the assistant called while producing this message (filled while streaming). */
  toolsUsed?: string[];
  /** True when streaming failed; the error text is appended to `content`. */
  error?: boolean;
  createdAt?: string;
}

/** Mirrors the backend `MessageResponse` returned by `/api/conversations/{id}/messages`. */
export interface MessageResponse {
  id: string;
  conversationId: string;
  role: MessageRole;
  content: string;
  reasoningContent?: string | null;
  /** JSON array of `{ tool, input, timestamp, resultSummary? }` records, or null without tools. */
  toolCalls?: string | null;
  tokenCount?: number | null;
  createdAt?: string;
}

/** Mirrors the backend `ChatEvent` sent over `/api/chat/stream`. */
export interface ChatEvent {
  type: "TOOL_START" | "TOOL_END" | "REASONING" | "CONTENT" | "ERROR" | "DONE";
  content?: string;
  tool?: string;
  messageId?: string;
}

/** Mirrors the backend `ChatRequest`. */
export interface ChatRequest {
  conversationId: string;
  message: string;
  model: string;
  toolsEnabled: boolean;
}
