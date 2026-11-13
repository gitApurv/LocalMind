import type { Message, MessageResponse } from "../types";

interface ToolCallRecord {
  tool?: unknown;
}

/** Turns a persisted message into the UI shape, restoring the tool badges from `toolCalls`. */
export function toMessage({
  toolCalls,
  tokenCount: _tokenCount,
  ...rest
}: MessageResponse): Message {
  const toolsUsed = parseToolNames(toolCalls);
  return toolsUsed.length > 0 ? { ...rest, toolsUsed } : rest;
}

/** Distinct tool names, in call order, from the backend's tool-call audit JSON. */
export function parseToolNames(toolCalls: string | null | undefined): string[] {
  if (!toolCalls) return [];
  try {
    const calls: unknown = JSON.parse(toolCalls);
    if (!Array.isArray(calls)) return [];
    const names = calls
      .map((call: ToolCallRecord) => call?.tool)
      .filter((name): name is string => typeof name === "string" && name.length > 0);
    return [...new Set(names)];
  } catch {
    // A malformed audit trail only costs the badges, never the message.
    return [];
  }
}
