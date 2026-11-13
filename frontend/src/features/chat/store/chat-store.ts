import { create } from "zustand";
import { chatApi } from "../api/chat-api";
import { toMessage } from "../lib/to-message";
import type { Message } from "../types";

export interface ChatState {
  messages: Message[];
  isLoadingMessages: boolean;
  isStreaming: boolean;
  /** Tool the model is running right now, shown as a live badge. */
  activeTool: string | null;
  /** Seconds the model spent reasoning before the first content token. */
  reasoningDuration: number | null;

  /**
   * Replaces the thread with the messages of `conversationId` (empty for null). Stops a running
   * stream first so its tokens cannot land in the newly opened thread.
   */
  loadMessages: (conversationId: string | null) => Promise<void>;
  /** Shows an empty thread, e.g. for a freshly created conversation; stops a running stream. */
  clearMessages: () => void;
  addMessage: (message: Message) => void;
  /** Marks a generation as running and returns the signal that cancels its request. */
  startStream: () => AbortSignal;
  /** Cancels the running generation (Stop button); the backend keeps the partial reply. */
  stopStream: () => void;
  /** Called when the stream of `signal` has ended; ignored if a newer stream has started. */
  finishStream: (signal: AbortSignal) => void;
  setActiveTool: (tool: string) => void;
  completeTool: (tool: string) => void;
  appendReasoning: (text: string) => void;
  appendContent: (text: string) => void;
  setReasoningDuration: (seconds: number) => void;
  /** Marks the streamed message as failed and appends the error text. */
  failLastMessage: (errorMessage: string) => void;
  /** Swaps the temporary id of the streamed message for the id the backend persisted. */
  confirmLastMessage: (id: string) => void;
  reset: () => void;
}

const emptyThread = (): Pick<ChatState, "messages" | "activeTool" | "reasoningDuration"> => ({
  messages: [],
  activeTool: null,
  reasoningDuration: null,
});

const streamStopped = { isStreaming: false, activeTool: null } as const;

export const useChatStore = create<ChatState>((set) => {
  // The controller of the running generation; kept out of state because it is not renderable.
  let streamController: AbortController | null = null;

  const abortStream = () => {
    streamController?.abort();
    streamController = null;
  };

  const updateLastMessage = (update: (message: Message) => Message) =>
    set((state) => {
      if (state.messages.length === 0) return state;
      const messages = [...state.messages];
      messages[messages.length - 1] = update(messages[messages.length - 1]);
      return { messages };
    });

  return {
    ...emptyThread(),
    isLoadingMessages: false,
    isStreaming: false,

    loadMessages: async (conversationId) => {
      abortStream();
      set({ ...emptyThread(), ...streamStopped });
      if (!conversationId) return;

      set({ isLoadingMessages: true });
      try {
        const messages = await chatApi.listMessages(conversationId);
        set({ messages: (messages ?? []).map(toMessage), isLoadingMessages: false });
      } catch (error) {
        console.error("Failed to load messages for conversation:", error);
        set({ isLoadingMessages: false });
      }
    },

    clearMessages: () => {
      abortStream();
      set({ ...emptyThread(), ...streamStopped });
    },

    addMessage: (message) => set((state) => ({ messages: [...state.messages, message] })),

    startStream: () => {
      abortStream();
      streamController = new AbortController();
      set({ isStreaming: true, activeTool: null, reasoningDuration: null });
      return streamController.signal;
    },

    stopStream: () => {
      abortStream();
      set(streamStopped);
    },

    finishStream: (signal) => {
      if (streamController && streamController.signal !== signal) return;
      streamController = null;
      set(streamStopped);
    },

    setActiveTool: (tool) => set({ activeTool: tool }),

    completeTool: (tool) => {
      set({ activeTool: null });
      updateLastMessage((message) => {
        const tools = message.toolsUsed ?? [];
        return tools.includes(tool) ? message : { ...message, toolsUsed: [...tools, tool] };
      });
    },

    appendReasoning: (text) =>
      updateLastMessage((message) => ({
        ...message,
        reasoningContent: (message.reasoningContent ?? "") + text,
      })),

    appendContent: (text) =>
      updateLastMessage((message) => ({ ...message, content: (message.content ?? "") + text })),

    setReasoningDuration: (seconds) => set({ reasoningDuration: seconds }),

    failLastMessage: (errorMessage) =>
      updateLastMessage((message) => ({
        ...message,
        error: true,
        content: message.content
          ? `${message.content}\n\n⚠️ ${errorMessage}`
          : `⚠️ ${errorMessage}`,
      })),

    confirmLastMessage: (id) => updateLastMessage((message) => ({ ...message, id })),

    reset: () => {
      abortStream();
      set({ ...emptyThread(), ...streamStopped, isLoadingMessages: false });
    },
  };
});
