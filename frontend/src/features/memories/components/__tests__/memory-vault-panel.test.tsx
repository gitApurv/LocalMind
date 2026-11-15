import { act, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { useMemoryStore } from "../../store/memory-store";
import { MemoryVaultPanel } from "../memory-vault-panel";

describe("MemoryVaultPanel", () => {
  beforeEach(() => {
    useMemoryStore.setState({
      memories: [
        {
          id: "m1",
          memoryFact: "Uses PostgreSQL 14",
          pinned: true,
          createdAt: "2026-11-15T00:00:00Z",
        },
        {
          id: "m2",
          memoryFact: "Prefers TypeScript",
          pinned: false,
          createdAt: "2026-11-15T00:00:00Z",
        },
      ],
      isLoading: false,
      error: null,
    });
  });

  it("renders memory items and filter search", async () => {
    await act(async () => {
      render(<MemoryVaultPanel onClose={() => {}} />);
    });
    expect(screen.getByText("Uses PostgreSQL 14")).toBeDefined();
    expect(screen.getByText("Prefers TypeScript")).toBeDefined();
    expect(screen.getByPlaceholderText(/filter memories/i)).toBeDefined();
  });
});
