import { describe, expect, it } from "vitest";
import type { MessageResponse } from "../../types";
import { parseToolCallsData, parseToolNames, toMessage } from "../to-message";

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
  it("restores tool badges and audit records from the persisted tool-call JSON", () => {
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
      tokenCount: 12,
      toolsUsed: ["searchWeb", "getCurrentDateTime"],
      toolCallsData: [
        { tool: "searchWeb", input: "{}", timestamp: "t1", resultSummary: "3 results" },
        { tool: "getCurrentDateTime", input: "{}", timestamp: "t2" },
        { tool: "searchWeb", input: "{}", timestamp: "t3" },
      ],
    });
  });

  it("leaves toolsUsed and toolCallsData out when no tool was called", () => {
    expect(toMessage(response)).toEqual({
      id: "m-1",
      conversationId: "c-1",
      role: "ASSISTANT",
      content: "It is 21:00.",
      reasoningContent: null,
      createdAt: "2026-01-01T00:00:00Z",
      tokenCount: 12,
    });
  });
});

describe("parseToolCallsData", () => {
  it("extracts full tool audit records", () => {
    const raw = JSON.stringify([
      {
        tool: "searchWeb",
        input: "Citus 12",
        timestamp: "2026-11-15T00:00:00Z",
        resultSummary: "Found 3 results",
      },
    ]);
    const parsed = parseToolCallsData(raw);
    expect(parsed).toHaveLength(1);
    expect(parsed[0].tool).toBe("searchWeb");
    expect(parsed[0].input).toBe("Citus 12");
    expect(parsed[0].resultSummary).toBe("Found 3 results");
  });

  it("cleans literal escaped newlines and outer quotes in resultSummary", () => {
    const raw = JSON.stringify([
      {
        tool: "recallUserMemories",
        input: '{"query":"Who am I?"}',
        resultSummary:
          "\"Recalled User Memories for 'Who am I?':\\n1. I am a Java Developer with experience in building AI applications.\"",
      },
    ]);
    const parsed = parseToolCallsData(raw);
    expect(parsed[0].resultSummary).toBe(
      "Recalled User Memories for 'Who am I?':\n1. I am a Java Developer with experience in building AI applications."
    );
  });

  it("handles malformed JSON gracefully", () => {
    expect(parseToolCallsData("invalid")).toEqual([]);
    expect(parseToolCallsData(null)).toEqual([]);
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
