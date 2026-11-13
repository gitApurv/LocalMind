import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { ConversationList } from "../conversation-list";
import { useConversationStore } from "../../store/conversation-store";
import { apiClient } from "@/lib/api-client";

describe("ConversationList", () => {
  beforeEach(() => {
    useConversationStore.setState({
      conversations: [
        {
          id: "c-1",
          userId: "u-1",
          title: "Pinned Knowledge Base",
          modelName: "gemma3:1b",
          pinned: true,
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
        },
        {
          id: "c-2",
          userId: "u-1",
          title: "Recent Questions",
          modelName: "gemma3:1b",
          pinned: false,
          createdAt: "2026-01-02T00:00:00Z",
          updatedAt: "2026-01-02T00:00:00Z",
        },
      ],
      activeConversationId: "c-1",
    });
    vi.restoreAllMocks();
    vi.spyOn(apiClient, "get").mockResolvedValue([]);
  });

  it("renders pinned and regular threads in separate sections", () => {
    render(<ConversationList />);

    expect(screen.getByText("Pinned Knowledge Base")).toBeDefined();
    expect(screen.getByText("Recent Questions")).toBeDefined();
    expect(screen.getByText("Pinned")).toBeDefined();
  });

  it("selects conversation when clicked", async () => {
    const selectSpy = vi.spyOn(useConversationStore.getState(), "selectConversation");
    render(<ConversationList />);

    await waitFor(() => {
      expect(screen.getByText("Recent Questions")).toBeDefined();
    });

    await act(async () => {
      fireEvent.click(screen.getByText("Recent Questions"));
    });
    expect(selectSpy).toHaveBeenCalledWith("c-2");
  });

  it("allows inline renaming of conversation title", async () => {
    vi.spyOn(apiClient, "patch").mockResolvedValueOnce({
      id: "c-1",
      title: "Updated Title",
    });

    render(<ConversationList />);

    // Click edit title button
    const editBtn = screen.getByLabelText(/rename conversation pinned knowledge base/i);
    fireEvent.click(editBtn);

    const input = screen.getByRole("textbox", { name: /edit conversation title/i });
    expect(input).toBeDefined();

    fireEvent.change(input, { target: { value: "Updated Title" } });
    fireEvent.keyDown(input, { key: "Enter", code: "Enter" });

    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith("/api/conversations/c-1", {
        title: "Updated Title",
      });
    });
  });

  it("toggles pin status on conversation", async () => {
    vi.spyOn(apiClient, "patch").mockResolvedValueOnce({
      id: "c-2",
      pinned: true,
    });

    render(<ConversationList />);

    const pinBtn = screen.getByLabelText(/pin conversation recent questions/i);
    fireEvent.click(pinBtn);

    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith("/api/conversations/c-2", { pinned: true });
    });
  });

  it("deletes conversation", async () => {
    vi.spyOn(apiClient, "delete").mockResolvedValueOnce({});

    render(<ConversationList />);

    const deleteBtn = screen.getByLabelText(/delete conversation recent questions/i);
    fireEvent.click(deleteBtn);

    await waitFor(() => {
      expect(apiClient.delete).toHaveBeenCalledWith("/api/conversations/c-2");
    });
  });
});
