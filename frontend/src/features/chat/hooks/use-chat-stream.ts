import { useCallback, useState } from "react";
import { getErrorMessage, isAbortError } from "@/lib/api-error";
import { DEFAULT_CONVERSATION_TITLE } from "@/features/conversations/constants";
import { useConversationStore } from "@/features/conversations/store/conversation-store";
import { useModelStore } from "@/features/models/store/model-store";
import { chatApi } from "../api/chat-api";
import { readChatEvents } from "../lib/sse-parser";
import { useChatStore } from "../store/chat-store";
import type { ChatEvent, Message } from "../types";

// The backend titles a new conversation asynchronously after the first exchange.
const TITLE_REFRESH_DELAY_MS = 1500;

interface StreamSession {
  reasoningStartedAt: number | null;
  reportError: (message: string) => void;
}

/** Sends prompts to `/api/chat/stream` and applies the streamed events to the chat store. */
export function useChatStream() {
  const [error, setError] = useState<string | null>(null);
  const stopStreaming = useCallback(() => useChatStore.getState().stopStream(), []);

  const sendMessage = useCallback(async (prompt: string) => {
    const text = prompt.trim();
    if (!text || useChatStore.getState().isStreaming) return;
    setError(null);

    // Only await when a conversation must be created: isStreaming has to flip in the same tick
    // so a second Enter cannot send twice.
    let conversationId = useConversationStore.getState().activeConversationId;
    if (!conversationId) {
      try {
        conversationId = (await useConversationStore.getState().createConversation()).id;
      } catch (err) {
        setError(getErrorMessage(err, "Failed to initialize conversation"));
        return;
      }
    }

    const chat = useChatStore.getState();
    chat.addMessage(createMessage(conversationId, "USER", text));
    chat.addMessage({
      ...createMessage(conversationId, "ASSISTANT", ""),
      reasoningContent: "",
      toolsUsed: [],
    });
    // Switching threads also aborts this signal (see the chat store), not only the Stop button.
    const signal = chat.startStream();
    const session: StreamSession = {
      reasoningStartedAt: null,
      reportError: (message) => {
        setError(message);
        useChatStore.getState().failLastMessage(message);
      },
    };

    try {
      const { selectedModel, toolsEnabled } = useModelStore.getState();
      const response = await chatApi.stream(
        { conversationId, message: text, model: selectedModel, toolsEnabled },
        signal
      );
      if (!response.body) {
        throw new Error("No response body received from server");
      }
      for await (const event of readChatEvents(response.body, signal)) {
        // Events already parsed from the last chunk must not reach the thread opened since.
        if (signal.aborted) break;
        applyChatEvent(event, session);
      }
    } catch (err) {
      if (!signal.aborted && !isAbortError(err)) {
        session.reportError(
          getErrorMessage(err, "An unexpected error occurred during chat streaming")
        );
      }
    } finally {
      useChatStore.getState().finishStream(signal);
      scheduleTitleRefresh(conversationId);
    }
  }, []);

  return { sendMessage, stopStreaming, error };
}

function createMessage(conversationId: string, role: Message["role"], content: string): Message {
  return {
    id: `${role.toLowerCase()}-${Date.now()}`,
    conversationId,
    role,
    content,
    createdAt: new Date().toISOString(),
  };
}

function applyChatEvent(event: ChatEvent, session: StreamSession): void {
  const chat = useChatStore.getState();
  switch (event.type) {
    case "TOOL_START":
      if (event.tool) chat.setActiveTool(event.tool);
      return;
    case "TOOL_END":
      if (event.tool) chat.completeTool(event.tool);
      return;
    case "REASONING":
      session.reasoningStartedAt ??= Date.now();
      chat.appendReasoning(event.content ?? "");
      return;
    case "CONTENT":
      if (session.reasoningStartedAt !== null && chat.reasoningDuration === null) {
        chat.setReasoningDuration(secondsSince(session.reasoningStartedAt));
      }
      chat.appendContent(event.content ?? "");
      return;
    case "ERROR":
      session.reportError(event.content || "An error occurred during inference");
      return;
    case "DONE":
      if (event.messageId) chat.confirmLastMessage(event.messageId);
      return;
  }
}

function secondsSince(start: number): number {
  return Number(Math.max(0.1, (Date.now() - start) / 1000).toFixed(1));
}

function scheduleTitleRefresh(conversationId: string): void {
  const { conversations, loadConversations } = useConversationStore.getState();
  const conversation = conversations.find((c) => c.id === conversationId);
  if (conversation && (!conversation.title || conversation.title === DEFAULT_CONVERSATION_TITLE)) {
    setTimeout(() => void loadConversations(), TITLE_REFRESH_DELAY_MS);
  }
}
