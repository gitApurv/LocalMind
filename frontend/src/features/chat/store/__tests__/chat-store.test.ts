import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/lib/api-client";
import { aMessage, resetWorkspaceStores } from "@/test/factories";
import { useChatStore } from "../chat-store";

const assistantPlaceholder = aMessage({ id: "a-tmp", role: "ASSISTANT", content: "" });

describe("useChatStore", () => {
  beforeEach(() => {
    resetWorkspaceStores();
    vi.restoreAllMocks();
  });

  it("loadMessages() replaces the thread with the conversation's messages", async () => {
    useChatStore.setState({ messages: [aMessage({ content: "old" })], activeTool: "searchWeb" });
    const messages = [aMessage({ id: "m-9", content: "persisted" })];
    vi.spyOn(apiClient, "get").mockResolvedValueOnce(messages);

    await useChatStore.getState().loadMessages("c-1");

    expect(useChatStore.getState().messages).toEqual(messages);
    expect(useChatStore.getState().activeTool).toBeNull();
    expect(useChatStore.getState().isLoadingMessages).toBe(false);
  });

  it("loadMessages() restores tool badges of persisted assistant messages", async () => {
    vi.spyOn(apiClient, "get").mockResolvedValueOnce([
      {
        id: "a-1",
        conversationId: "c-1",
        role: "ASSISTANT",
        content: "Found it.",
        toolCalls: '[{"tool":"searchWeb","input":"{}","timestamp":"2026-01-01T00:00:00Z"}]',
        tokenCount: 7,
      },
    ]);

    await useChatStore.getState().loadMessages("c-1");

    expect(useChatStore.getState().messages[0].toolsUsed).toEqual(["searchWeb"]);
  });

  it("loadMessages(null) clears the thread without a request", async () => {
    useChatStore.setState({ messages: [aMessage()] });
    const getSpy = vi.spyOn(apiClient, "get");

    await useChatStore.getState().loadMessages(null);

    expect(useChatStore.getState().messages).toEqual([]);
    expect(getSpy).not.toHaveBeenCalled();
  });

  it("streaming actions update only the last message", () => {
    useChatStore.setState({ messages: [aMessage(), assistantPlaceholder] });
    const chat = useChatStore.getState();

    chat.setActiveTool("searchWeb");
    expect(useChatStore.getState().activeTool).toBe("searchWeb");
    chat.completeTool("searchWeb");
    chat.completeTool("searchWeb");
    chat.appendReasoning("Let me ");
    chat.appendReasoning("think.");
    chat.appendContent("Answer");
    chat.confirmLastMessage("msg-real");

    const [user, assistant] = useChatStore.getState().messages;
    expect(user).toEqual(aMessage());
    expect(assistant).toMatchObject({
      id: "msg-real",
      content: "Answer",
      reasoningContent: "Let me think.",
      toolsUsed: ["searchWeb"],
    });
    expect(useChatStore.getState().activeTool).toBeNull();
  });

  it("failLastMessage() flags the message and appends the error", () => {
    useChatStore.setState({ messages: [{ ...assistantPlaceholder, content: "Partial" }] });

    useChatStore.getState().failLastMessage("Context overflow");

    expect(useChatStore.getState().messages[0]).toMatchObject({
      error: true,
      content: "Partial\n\n⚠️ Context overflow",
    });
  });

  it("startStream() and finishStream() toggle the streaming indicators", () => {
    useChatStore.setState({ reasoningDuration: 3 });

    const signal = useChatStore.getState().startStream();
    expect(useChatStore.getState()).toMatchObject({ isStreaming: true, reasoningDuration: null });

    useChatStore.getState().setActiveTool("calculateExpression");
    useChatStore.getState().finishStream(signal);
    expect(useChatStore.getState()).toMatchObject({ isStreaming: false, activeTool: null });
    expect(signal.aborted).toBe(false);
  });

  it("stopStream() aborts the running generation", () => {
    const signal = useChatStore.getState().startStream();

    useChatStore.getState().stopStream();

    expect(signal.aborted).toBe(true);
    expect(useChatStore.getState().isStreaming).toBe(false);
  });

  it.each([
    ["loadMessages", () => useChatStore.getState().loadMessages(null)],
    ["clearMessages", () => useChatStore.getState().clearMessages()],
    ["reset", () => useChatStore.getState().reset()],
  ])("%s() aborts a running stream so it cannot write into the next thread", async (_, act) => {
    const signal = useChatStore.getState().startStream();

    await act();

    expect(signal.aborted).toBe(true);
    expect(useChatStore.getState().isStreaming).toBe(false);
  });

  it("finishStream() of an older stream leaves a newer stream running", () => {
    const oldSignal = useChatStore.getState().startStream();
    useChatStore.getState().stopStream();
    const newSignal = useChatStore.getState().startStream();

    useChatStore.getState().finishStream(oldSignal);

    expect(useChatStore.getState().isStreaming).toBe(true);
    expect(newSignal.aborted).toBe(false);
  });
});
