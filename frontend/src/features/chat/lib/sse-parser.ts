import type { ChatEvent } from "../types";

// Some proxies rewrite "data:" as "data :".
const DATA_PREFIX = /^data ?:/;

/**
 * Incremental parser for `text/event-stream` chunks. Chunks may split events anywhere; complete
 * events are returned as soon as their blank-line terminator arrives. CRLF is accepted.
 */
export function createSseParser() {
  let buffer = "";

  return {
    push(chunk: string): ChatEvent[] {
      buffer = (buffer + chunk).replace(/\r\n/g, "\n");
      const blocks = buffer.split("\n\n");
      buffer = blocks.pop() ?? "";
      return blocks.flatMap((block) => block.split("\n").flatMap(parseDataLine));
    },
  };
}

function parseDataLine(rawLine: string): ChatEvent[] {
  const line = rawLine.trim();
  const prefix = DATA_PREFIX.exec(line);
  if (!prefix) return [];

  const json = line.slice(prefix[0].length).trim();
  if (!json) return [];

  try {
    return [JSON.parse(json) as ChatEvent];
  } catch (error) {
    console.warn("Failed to parse SSE JSON chunk:", json, error);
    return [];
  }
}

/** Yields the chat events of a response body; aborting `signal` ends the iteration. */
export async function* readChatEvents(
  body: ReadableStream<Uint8Array>,
  signal?: AbortSignal
): AsyncGenerator<ChatEvent> {
  const reader = body.getReader();
  const cancel = () => {
    reader.cancel().catch(() => undefined);
  };
  signal?.addEventListener("abort", cancel);

  const decoder = new TextDecoder();
  const parser = createSseParser();
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) return;
      yield* parser.push(decoder.decode(value, { stream: true }));
    }
  } finally {
    signal?.removeEventListener("abort", cancel);
  }
}
