import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ChatContainer } from "../chat-container";
import { useConversationStore } from "@/features/conversations/store/conversation-store";
import { aConversation, resetWorkspaceStores } from "@/test/factories";
import { useChatStore } from "../../store/chat-store";
import { apiClient } from "@/lib/api-client";

describe("ChatContainer", () => {
  beforeEach(() => {
    resetWorkspaceStores();
    useConversationStore.setState({
      conversations: [aConversation({ title: "Architecture Discussion" })],
      activeConversationId: "c-1",
    });
    vi.restoreAllMocks();
    vi.spyOn(apiClient, "get").mockResolvedValue([]);
  });

  it("renders conversation title and empty state welcome cards", () => {
    render(<ChatContainer />);

    expect(screen.getByText("Architecture Discussion")).toBeDefined();
    expect(screen.getByText(/how can i help you today/i)).toBeDefined();
  });

  it("submits message when send button is clicked", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      body: new ReadableStream({
        start(controller) {
          controller.enqueue(
            new TextEncoder().encode('data: {"type":"DONE","messageId":"msg-done"}\n\n')
          );
          controller.close();
        },
      }),
    } as unknown as Response);

    render(<ChatContainer />);

    const textarea = screen.getByPlaceholderText(/message localmind/i);
    fireEvent.change(textarea, { target: { value: "Tell me about quantum computing." } });

    const sendBtn = screen.getByRole("button", { name: /send message/i });
    fireEvent.click(sendBtn);

    await waitFor(() => {
      const msgs = useChatStore.getState().messages;
      expect(msgs.length).toBeGreaterThan(0);
      expect(msgs[0].content).toBe("Tell me about quantum computing.");
    });
  });

  it("displays stop button when stream is active", () => {
    useChatStore.setState({ isStreaming: true });

    render(<ChatContainer />);

    expect(screen.getByRole("button", { name: /stop generation/i })).toBeDefined();
  });

  it("toggles left sidebar when sidebar toggle button is clicked", () => {
    const toggleSpy = vi.fn();
    render(<ChatContainer onToggleSidebar={toggleSpy} />);

    const toggleBtn = screen.getByRole("button", { name: /toggle navigation sidebar/i });
    fireEvent.click(toggleBtn);

    expect(toggleSpy).toHaveBeenCalled();
  });

  it("toggles right contextual inspector when inspector button is clicked", () => {
    render(<ChatContainer />);

    const inspectorToggle = screen.getByRole("button", { name: /toggle inspector drawer/i });
    fireEvent.click(inspectorToggle);

    expect(screen.getByLabelText("Contextual Inspector")).toBeDefined();
  });
});
