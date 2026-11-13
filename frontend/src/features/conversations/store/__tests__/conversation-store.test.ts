import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/lib/api-client";
import { useChatStore } from "@/features/chat/store/chat-store";
import { useModelStore } from "@/features/models/store/model-store";
import { aConversation, aMessage, resetWorkspaceStores } from "@/test/factories";
import { useConversationStore } from "../conversation-store";

describe("useConversationStore", () => {
  beforeEach(() => {
    resetWorkspaceStores();
    vi.restoreAllMocks();
  });

  it("loadConversations() fetches and stores the list", async () => {
    const conversations = [aConversation({ title: "Test Chat 1" })];
    vi.spyOn(apiClient, "get").mockResolvedValueOnce(conversations);

    await useConversationStore.getState().loadConversations();

    expect(apiClient.get).toHaveBeenCalledWith("/api/conversations");
    expect(useConversationStore.getState().conversations).toEqual(conversations);
  });

  it("loadConversations() keeps the previous list when the request fails", async () => {
    useConversationStore.setState({ conversations: [aConversation()] });
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    vi.spyOn(apiClient, "get").mockRejectedValueOnce({ status: 0, message: "offline" });

    await useConversationStore.getState().loadConversations();

    expect(useConversationStore.getState().conversations).toHaveLength(1);
  });

  it("createConversation() defaults to the placeholder title and the selected model", async () => {
    useModelStore.setState({ selectedModel: "qwen2.5:7b" });
    useChatStore.setState({ messages: [aMessage()] });
    const created = aConversation({ id: "c-new", title: "New Chat", modelName: "qwen2.5:7b" });
    const postSpy = vi.spyOn(apiClient, "post").mockResolvedValueOnce(created);

    const result = await useConversationStore.getState().createConversation();

    expect(postSpy).toHaveBeenCalledWith("/api/conversations", {
      title: "New Chat",
      modelName: "qwen2.5:7b",
    });
    expect(result).toEqual(created);
    expect(useConversationStore.getState().conversations[0].id).toBe("c-new");
    expect(useConversationStore.getState().activeConversationId).toBe("c-new");
    expect(useChatStore.getState().messages).toEqual([]);
  });

  it("selectConversation() activates the thread, loads its messages and switches model", async () => {
    useModelStore.setState({ selectedModel: "qwen2.5:7b", toolsEnabled: true });
    useConversationStore.setState({
      conversations: [aConversation({ id: "c-1", modelName: "gemma3:1b" })],
    });
    const messages = [aMessage({ content: "Persisted" })];
    vi.spyOn(apiClient, "get").mockResolvedValueOnce(messages);

    await useConversationStore.getState().selectConversation("c-1");

    expect(apiClient.get).toHaveBeenCalledWith("/api/conversations/c-1/messages");
    expect(useConversationStore.getState().activeConversationId).toBe("c-1");
    expect(useChatStore.getState().messages).toEqual(messages);
    expect(useModelStore.getState().selectedModel).toBe("gemma3:1b");
    // gemma cannot call tools, so the switch must not leave tools enabled for the request.
    expect(useModelStore.getState().toolsEnabled).toBe(false);
  });

  it("deleteConversation() of the active thread activates the next one", async () => {
    useConversationStore.setState({
      conversations: [aConversation({ id: "c-1" }), aConversation({ id: "c-2" })],
      activeConversationId: "c-1",
    });
    vi.spyOn(apiClient, "delete").mockResolvedValueOnce({});
    vi.spyOn(apiClient, "get").mockResolvedValueOnce([]);

    await useConversationStore.getState().deleteConversation("c-1");

    const state = useConversationStore.getState();
    expect(state.conversations.map((c) => c.id)).toEqual(["c-2"]);
    expect(state.activeConversationId).toBe("c-2");
  });

  it("deleteConversation() of an inactive thread keeps the active one and its messages", async () => {
    useConversationStore.setState({
      conversations: [aConversation({ id: "c-1" }), aConversation({ id: "c-2" })],
      activeConversationId: "c-1",
    });
    useChatStore.setState({ messages: [aMessage({ content: "Keep this" })] });
    vi.spyOn(apiClient, "delete").mockResolvedValueOnce({});
    const getSpy = vi.spyOn(apiClient, "get");

    await useConversationStore.getState().deleteConversation("c-2");

    expect(useConversationStore.getState().activeConversationId).toBe("c-1");
    expect(getSpy).not.toHaveBeenCalled();
    expect(useChatStore.getState().messages[0].content).toBe("Keep this");
  });

  it("deleteConversation() keeps the thread when the request fails", async () => {
    useConversationStore.setState({ conversations: [aConversation({ id: "c-1" })] });
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    vi.spyOn(apiClient, "delete").mockRejectedValueOnce({ status: 500, message: "boom" });

    await useConversationStore.getState().deleteConversation("c-1");

    expect(useConversationStore.getState().conversations).toHaveLength(1);
  });

  it("togglePin() sends the inverted flag and stores the response", async () => {
    useConversationStore.setState({ conversations: [aConversation({ id: "c-1", pinned: false })] });
    const patchSpy = vi
      .spyOn(apiClient, "patch")
      .mockResolvedValueOnce(aConversation({ id: "c-1", pinned: true }));

    await useConversationStore.getState().togglePin("c-1");

    expect(patchSpy).toHaveBeenCalledWith("/api/conversations/c-1", { pinned: true });
    expect(useConversationStore.getState().conversations[0].pinned).toBe(true);
  });

  it("renameConversation() stores the new title", async () => {
    useConversationStore.setState({ conversations: [aConversation({ id: "c-1" })] });
    vi.spyOn(apiClient, "patch").mockResolvedValueOnce(
      aConversation({ id: "c-1", title: "Renamed" })
    );

    await useConversationStore.getState().renameConversation("c-1", "Renamed");

    expect(useConversationStore.getState().conversations[0].title).toBe("Renamed");
  });

  it("reset() clears conversations and the open thread", () => {
    useConversationStore.setState({
      conversations: [aConversation()],
      activeConversationId: "c-1",
    });
    useChatStore.setState({ messages: [aMessage()], isStreaming: true });

    useConversationStore.getState().reset();

    expect(useConversationStore.getState().conversations).toEqual([]);
    expect(useConversationStore.getState().activeConversationId).toBeNull();
    expect(useChatStore.getState().messages).toEqual([]);
    expect(useChatStore.getState().isStreaming).toBe(false);
  });
});
