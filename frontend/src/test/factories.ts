import { useChatStore } from "@/features/chat/store/chat-store";
import type { Message } from "@/features/chat/types";
import { useConversationStore } from "@/features/conversations/store/conversation-store";
import type { Conversation } from "@/features/conversations/types";
import { useModelStore } from "@/features/models/store/model-store";

export function aConversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    id: "c-1",
    userId: "u-1",
    title: "Chat 1",
    modelName: "gemma3:1b",
    pinned: false,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

export function aMessage(overrides: Partial<Message> = {}): Message {
  return { id: "m-1", conversationId: "c-1", role: "USER", content: "Hello", ...overrides };
}

/** Puts the conversation, chat and model stores into a known state; actions are kept. */
export function resetWorkspaceStores(): void {
  useConversationStore.setState({ conversations: [], activeConversationId: null });
  useChatStore.setState({
    messages: [],
    isLoadingMessages: false,
    isStreaming: false,
    activeTool: null,
    reasoningDuration: null,
  });
  useModelStore.setState({
    availableModels: ["gemma3:1b"],
    selectedModel: "gemma3:1b",
    modelCapabilities: { "gemma3:1b": false, "deepseek-r1:8b": false, "qwen2.5:7b": true },
    toolsEnabled: false,
  });
}
