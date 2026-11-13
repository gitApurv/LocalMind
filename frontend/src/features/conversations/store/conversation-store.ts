import { create } from "zustand";
import { useChatStore } from "@/features/chat/store/chat-store";
import { useModelStore } from "@/features/models/store/model-store";
import { conversationsApi } from "../api/conversations-api";
import { DEFAULT_CONVERSATION_TITLE } from "../constants";
import type { Conversation, UpdateConversationRequest } from "../types";

export interface ConversationState {
  conversations: Conversation[];
  activeConversationId: string | null;

  loadConversations: () => Promise<void>;
  /** Activates a conversation (null for a blank thread), its model and its messages. */
  selectConversation: (id: string | null) => Promise<void>;
  /** Creates and activates a conversation; rejects with an `ApiError` on failure. */
  createConversation: (title?: string, modelName?: string) => Promise<Conversation>;
  renameConversation: (id: string, title: string) => Promise<void>;
  togglePin: (id: string) => Promise<void>;
  /** Deletes a conversation and, if it was active, activates the next one. */
  deleteConversation: (id: string) => Promise<void>;
  /** Clears all conversation and chat state, e.g. on sign-out. */
  reset: () => void;
}

export const useConversationStore = create<ConversationState>((set, get) => {
  const update = async (id: string, request: UpdateConversationRequest) => {
    const updated = await conversationsApi.update(id, request);
    set((state) => ({
      conversations: state.conversations.map((c) => (c.id === id ? { ...c, ...updated } : c)),
    }));
  };

  return {
    conversations: [],
    activeConversationId: null,

    loadConversations: async () => {
      try {
        const conversations = await conversationsApi.list();
        set({ conversations: conversations ?? [] });
      } catch (error) {
        console.error("Failed to load conversations:", error);
      }
    },

    selectConversation: async (id) => {
      const conversation = get().conversations.find((c) => c.id === id);
      set({ activeConversationId: id });
      if (conversation?.modelName) {
        useModelStore.getState().setSelectedModel(conversation.modelName);
      }
      await useChatStore.getState().loadMessages(id);
    },

    createConversation: async (title = DEFAULT_CONVERSATION_TITLE, modelName) => {
      const conversation = await conversationsApi.create({
        title,
        modelName: modelName || useModelStore.getState().selectedModel,
      });
      set((state) => ({
        conversations: [conversation, ...state.conversations],
        activeConversationId: conversation.id,
      }));
      useChatStore.getState().clearMessages();
      return conversation;
    },

    renameConversation: async (id, title) => {
      try {
        await update(id, { title });
      } catch (error) {
        console.error("Failed to update conversation title:", error);
      }
    },

    togglePin: async (id) => {
      const conversation = get().conversations.find((c) => c.id === id);
      if (!conversation) return;
      try {
        await update(id, { pinned: !conversation.pinned });
      } catch (error) {
        console.error("Failed to toggle conversation pin status:", error);
      }
    },

    deleteConversation: async (id) => {
      try {
        await conversationsApi.remove(id);
      } catch (error) {
        console.error("Failed to delete conversation:", error);
        return;
      }
      const wasActive = get().activeConversationId === id;
      const remaining = get().conversations.filter((c) => c.id !== id);
      set({ conversations: remaining });
      if (wasActive) {
        await get().selectConversation(remaining[0]?.id ?? null);
      }
    },

    reset: () => {
      set({ conversations: [], activeConversationId: null });
      useChatStore.getState().reset();
    },
  };
});
