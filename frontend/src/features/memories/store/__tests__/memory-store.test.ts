import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/lib/api-client";
import { useMemoryStore } from "../memory-store";

describe("useMemoryStore", () => {
  beforeEach(() => {
    useMemoryStore.setState({ memories: [], isLoading: false, error: null });
    vi.restoreAllMocks();
  });

  it("loads memories from API", async () => {
    vi.spyOn(apiClient, "get").mockResolvedValueOnce([
      { id: "m1", memoryFact: "Prefers Python", pinned: false, createdAt: "2026-11-15T00:00:00Z" },
    ]);
    await useMemoryStore.getState().fetchMemories();
    expect(useMemoryStore.getState().memories).toHaveLength(1);
    expect(useMemoryStore.getState().memories[0].memoryFact).toBe("Prefers Python");
  });

  it("toggles pin status optimistically", async () => {
    useMemoryStore.setState({
      memories: [
        { id: "m1", memoryFact: "Fact", pinned: false, createdAt: "2026-11-15T00:00:00Z" },
      ],
    });
    vi.spyOn(apiClient, "patch").mockResolvedValueOnce({
      id: "m1",
      memoryFact: "Fact",
      pinned: true,
      createdAt: "2026-11-15T00:00:00Z",
    });
    await useMemoryStore.getState().togglePin("m1");
    expect(useMemoryStore.getState().memories[0].pinned).toBe(true);
  });

  it("deletes a memory", async () => {
    useMemoryStore.setState({
      memories: [
        { id: "m1", memoryFact: "Fact", pinned: false, createdAt: "2026-11-15T00:00:00Z" },
      ],
    });
    vi.spyOn(apiClient, "delete").mockResolvedValueOnce(undefined);
    await useMemoryStore.getState().deleteMemory("m1");
    expect(useMemoryStore.getState().memories).toHaveLength(0);
  });
});
