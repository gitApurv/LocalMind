import * as React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useChatStore } from "../../store/chat-store";
import { ChatComposer } from "../chat-composer";

describe("ChatComposer", () => {
  const defaultProps = {
    value: "test message",
    onChange: vi.fn(),
    onSend: vi.fn(),
    onStop: vi.fn(),
    isStreaming: false,
    error: null,
    inputRef: { current: null } as React.RefObject<HTMLTextAreaElement | null>,
  };

  beforeEach(() => {
    useChatStore.setState({ activePersonaId: "default", customPersonaPrompt: "" });
    vi.restoreAllMocks();
  });

  it("renders persona chips and switches active persona", () => {
    render(<ChatComposer {...defaultProps} />);
    expect(screen.getByText("Default")).toBeDefined();
    expect(screen.getByText("Code Architect")).toBeDefined();

    const codeChip = screen.getByText("Code Architect");
    fireEvent.click(codeChip);
    expect(useChatStore.getState().activePersonaId).toBe("code_architect");
  });

  it("calls onSend when send button is clicked", () => {
    render(<ChatComposer {...defaultProps} />);
    const sendButton = screen.getByTitle(/send message/i);
    fireEvent.click(sendButton);
    expect(defaultProps.onSend).toHaveBeenCalled();
  });

  it("opens custom persona dialog and saves custom prompt", () => {
    render(<ChatComposer {...defaultProps} />);
    const customChip = screen.getByText("Custom Persona");
    fireEvent.click(customChip);

    expect(screen.getByText("Custom Conversation Persona")).toBeDefined();
    const textarea = screen.getByPlaceholderText(/You are an expert penetration tester/i);
    fireEvent.change(textarea, { target: { value: "You are a Rust compiler." } });
    fireEvent.click(screen.getByText("Apply Persona"));

    expect(useChatStore.getState().activePersonaId).toBe("custom");
    expect(useChatStore.getState().customPersonaPrompt).toBe("You are a Rust compiler.");
  });
});
