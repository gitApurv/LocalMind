import { create } from "zustand";
import { getErrorMessage } from "@/lib/api-error";
import { memoriesApi } from "../api/memories-api";
import type { Memory } from "../types";

export interface MemoryState {
  memories: Memory[];
  isLoading: boolean;
  error: string | null;
  fetchMemories: () => Promise<void>;
  togglePin: (id: string) => Promise<void>;
  deleteMemory: (id: string) => Promise<void>;
}

export const useMemoryStore = create<MemoryState>((set, get) => ({
  memories: [],
  isLoading: false,
  error: null,

  fetchMemories: async () => {
    set({ isLoading: true, error: null });
    try {
      const memories = await memoriesApi.list();
      set({ memories, isLoading: false });
    } catch (err) {
      set({ error: getErrorMessage(err, "Failed to load memories"), isLoading: false });
    }
  },

  togglePin: async (id: string) => {
    const memory = get().memories.find((m) => m.id === id);
    if (!memory) return;
    const nextPinned = !memory.pinned;

    set({
      memories: get().memories.map((m) => (m.id === id ? { ...m, pinned: nextPinned } : m)),
    });

    try {
      const updated = await memoriesApi.update(id, { pinned: nextPinned });
      set({
        memories: get().memories.map((m) => (m.id === id ? updated : m)),
      });
    } catch (err) {
      set({
        memories: get().memories.map((m) => (m.id === id ? memory : m)),
        error: getErrorMessage(err, "Failed to update memory pin status"),
      });
    }
  },

  deleteMemory: async (id: string) => {
    const previous = get().memories;
    set({ memories: previous.filter((m) => m.id !== id) });

    try {
      await memoriesApi.delete(id);
    } catch (err) {
      set({
        memories: previous,
        error: getErrorMessage(err, "Failed to delete memory"),
      });
    }
  },
}));
