import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MessageItem } from "../message-item";
import type { Message } from "../../types";

describe("MessageItem", () => {
  it("renders user message cleanly", () => {
    const userMsg: Message = {
      id: "u-1",
      conversationId: "c-1",
      role: "USER",
      content: "Explain quantum superposition in simple terms.",
      createdAt: "2026-01-01T10:00:00Z",
    };

    render(<MessageItem message={userMsg} />);

    expect(screen.getByText("Explain quantum superposition in simple terms.")).toBeDefined();
    expect(screen.getByText("You")).toBeDefined();
  });

  it("renders assistant message with markdown and tool badge", () => {
    const assistantMsg: Message = {
      id: "a-1",
      conversationId: "c-1",
      role: "ASSISTANT",
      content: "Superposition is **fundamental** to quantum mechanics.",
      toolsUsed: ["searchWeb"],
      reasoningContent: "Thinking about the best analogy...",
      createdAt: "2026-01-01T10:00:05Z",
    };

    render(<MessageItem message={assistantMsg} />);

    // Shows assistant header
    expect(screen.getByText("LocalMind")).toBeDefined();

    // Shows tool badge
    expect(screen.getByText(/used web search/i)).toBeDefined();

    // Shows reasoning
    expect(screen.getByText(/thinking about the best analogy/i)).toBeDefined();

    // Shows markdown content
    expect(screen.getByText(/fundamental/i)).toBeDefined();
  });

  it("renders code block with language header and copy button", async () => {
    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    });

    const assistantMsg: Message = {
      id: "a-2",
      conversationId: "c-1",
      role: "ASSISTANT",
      content:
        "Here is a Python function:\n\n```python\ndef hello_world():\n    return 'Hello LocalMind'\n```",
    };

    render(<MessageItem message={assistantMsg} />);

    // Language badge
    expect(screen.getByText("python")).toBeDefined();

    // Copy code button
    const copyBtn = screen.getByRole("button", { name: /copy code/i });
    expect(copyBtn).toBeDefined();

    fireEvent.click(copyBtn);
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
      "def hello_world():\n    return 'Hello LocalMind'"
    );

    await waitFor(() => {
      expect(screen.getByText(/copied/i)).toBeDefined();
    });
  });
});
