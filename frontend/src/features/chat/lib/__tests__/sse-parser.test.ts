import { describe, expect, it, vi } from "vitest";
import { createSseParser, readChatEvents } from "../sse-parser";

describe("createSseParser", () => {
  it("returns events only once their terminating blank line arrives", () => {
    const parser = createSseParser();

    expect(parser.push('data: {"type":"CONTENT","con')).toEqual([]);
    expect(parser.push('tent":"Hi"}\n')).toEqual([]);
    expect(parser.push('\ndata: {"type":"DONE","messageId":"m-1"}\n\n')).toEqual([
      { type: "CONTENT", content: "Hi" },
      { type: "DONE", messageId: "m-1" },
    ]);
  });

  it("accepts CRLF line endings and the 'data :' variant", () => {
    const parser = createSseParser();

    expect(parser.push('data :{"type":"REASONING","content":"x"}\r\n\r\n')).toEqual([
      { type: "REASONING", content: "x" },
    ]);
  });

  it("skips comments, other fields and malformed JSON", () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const parser = createSseParser();

    const events = parser.push(
      ': keep-alive\n\nevent: message\ndata: {not json}\n\ndata: {"type":"DONE"}\n\n'
    );

    expect(events).toEqual([{ type: "DONE" }]);
  });
});

describe("readChatEvents", () => {
  it("yields every event of a byte stream", async () => {
    const encoder = new TextEncoder();
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('data: {"type":"CONTENT","content":"a"}\n\nda'));
        controller.enqueue(encoder.encode('ta: {"type":"CONTENT","content":"b"}\n\n'));
        controller.close();
      },
    });

    const events = [];
    for await (const event of readChatEvents(body)) {
      events.push(event);
    }

    expect(events.map((e) => e.content)).toEqual(["a", "b"]);
  });
});
