import type { Message, MessageResponse, ToolCallRecord } from "../types";

interface RawToolCall {
  tool?: unknown;
  input?: unknown;
  timestamp?: unknown;
  resultSummary?: unknown;
}

/** Turns a persisted message into the UI shape, restoring the tool badges and audit records from `toolCalls`. */
export function toMessage({ toolCalls, tokenCount, ...rest }: MessageResponse): Message {
  const toolsUsed = parseToolNames(toolCalls);
  const toolCallsData = parseToolCallsData(toolCalls);
  const msg: Message = { ...rest };
  if (toolsUsed.length > 0) msg.toolsUsed = toolsUsed;
  if (toolCallsData.length > 0) msg.toolCallsData = toolCallsData;
  if (tokenCount != null) msg.tokenCount = tokenCount;
  return msg;
}

/** Distinct tool names, in call order, from the backend's tool-call audit JSON. */
export function parseToolNames(toolCalls: string | null | undefined): string[] {
  if (!toolCalls) return [];
  try {
    const calls: unknown = JSON.parse(toolCalls);
    if (!Array.isArray(calls)) return [];
    const names = calls
      .map((call: RawToolCall) => call?.tool)
      .filter((name): name is string => typeof name === "string" && name.length > 0);
    return [...new Set(names)];
  } catch {
    return [];
  }
}

/** Parses full tool audit records from the backend's tool-call audit JSON. */
export function parseToolCallsData(toolCalls: string | null | undefined): ToolCallRecord[] {
  if (!toolCalls) return [];
  try {
    const calls: unknown = JSON.parse(toolCalls);
    if (!Array.isArray(calls)) return [];
    return calls
      .filter(
        (call: unknown): call is RawToolCall =>
          typeof call === "object" &&
          call !== null &&
          typeof (call as RawToolCall).tool === "string"
      )
      .map((call: RawToolCall) => ({
        tool: String(call.tool),
        input: typeof call.input === "string" ? call.input : undefined,
        timestamp: typeof call.timestamp === "string" ? call.timestamp : undefined,
        resultSummary: cleanSummary(call.resultSummary),
      }));
  } catch {
    return [];
  }
}

function cleanSummary(raw: unknown): string | undefined {
  if (typeof raw !== "string" || !raw.trim()) return undefined;
  let text = raw.trim();
  if (text.startsWith('"') && text.endsWith('"')) {
    try {
      const parsed = JSON.parse(text);
      if (typeof parsed === "string") text = parsed;
    } catch {
      text = text.slice(1, -1);
    }
  }
  return text.replace(/\\r\\n/g, "\n").replace(/\\n/g, "\n");
}
