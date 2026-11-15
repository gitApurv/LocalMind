import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { useChatStore } from "../../store/chat-store";
import { ToolAuditPanel } from "../tool-audit-panel";

describe("ToolAuditPanel", () => {
  beforeEach(() => {
    useChatStore.setState({
      messages: [
        {
          id: "m1",
          conversationId: "c1",
          role: "ASSISTANT",
          content: "Here is your search result.",
          toolCallsData: [
            {
              tool: "searchWeb",
              input: JSON.stringify({ query: "Citus 12 replication" }),
              timestamp: "2026-11-15T00:00:00Z",
              resultSummary: "Found 4 results",
            },
          ],
        },
      ],
    });
  });

  it("renders aggregated tool call items and details", () => {
    render(<ToolAuditPanel onClose={() => {}} />);
    expect(screen.getByText("SearXNG Web Search")).toBeDefined();
    expect(screen.getByText(/Found 4 results/i)).toBeDefined();
    expect(screen.getByText(/Citus 12 replication/i)).toBeDefined();
  });

  it("renders empty state when no tool executions exist", () => {
    useChatStore.setState({ messages: [] });
    render(<ToolAuditPanel onClose={() => {}} />);
    expect(screen.getByText("No tool executions recorded")).toBeDefined();
  });
});
