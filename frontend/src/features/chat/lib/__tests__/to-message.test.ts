import { describe, expect, it } from "vitest";
import type { MessageResponse } from "../../types";
import { parseToolNames, toMessage } from "../to-message";

const response: MessageResponse = {
  id: "m-1",
  conversationId: "c-1",
  role: "ASSISTANT",
  content: "It is 21:00.",
  reasoningContent: null,
  toolCalls: null,
  tokenCount: 12,
  createdAt: "2026-01-01T00:00:00Z",
};

describe("toMessage", () => {
  it("restores tool badges from the persisted tool-call JSON", () => {
    const toolCalls = JSON.stringify([
      { tool: "searchWeb", input: "{}", timestamp: "t1", resultSummary: "3 results" },
      { tool: "getCurrentDateTime", input: "{}", timestamp: "t2" },
      { tool: "searchWeb", input: "{}", timestamp: "t3" },
    ]);

    expect(toMessage({ ...response, toolCalls })).toEqual({
      id: "m-1",
      conversationId: "c-1",
      role: "ASSISTANT",
      content: "It is 21:00.",
      reasoningContent: null,
      createdAt: "2026-01-01T00:00:00Z",
      toolsUsed: ["searchWeb", "getCurrentDateTime"],
    });
  });

  it("leaves toolsUsed out when no tool was called", () => {
    expect(toMessage(response)).not.toHaveProperty("toolsUsed");
  });
});

describe("parseToolNames", () => {
  it.each([
    [undefined, []],
    ["", []],
    ["not json", []],
    ['{"tool":"searchWeb"}', []],
    [
      '[{"input":"x"},{"tool":""},{"tool":42},null,{"tool":"calculateExpression"}]',
      ["calculateExpression"],
    ],
  ])("%s -> %o", (toolCalls, expected) => {
    expect(parseToolNames(toolCalls)).toEqual(expected);
  });
});
