import { describe, it, expect, beforeEach, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { apiClient } from "@/lib/api-client";
import type { Attachment } from "../../types";
import { useAttachments } from "../use-attachments";

function anAttachment(overrides: Partial<Attachment> = {}): Attachment {
  return {
    id: "att-1",
    conversationId: "c-1",
    fileName: "spec.pdf",
    fileType: "application/pdf",
    fileSize: 1024,
    status: "READY",
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("useAttachments", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("loads the attachments of the conversation", async () => {
    vi.spyOn(apiClient, "get").mockResolvedValueOnce([anAttachment()]);

    const { result } = renderHook(() => useAttachments("c-1"));

    await waitFor(() => expect(result.current.attachments).toHaveLength(1));
    expect(apiClient.get).toHaveBeenCalledWith("/api/conversations/c-1/attachments");
  });

  it("is empty without a conversation and does not call the backend", () => {
    const get = vi.spyOn(apiClient, "get");

    const { result } = renderHook(() => useAttachments(null));

    expect(result.current.attachments).toEqual([]);
    expect(get).not.toHaveBeenCalled();
  });

  it("never shows a late response for the previous conversation", async () => {
    let resolveFirst: (value: Attachment[]) => void = () => {};
    vi.spyOn(apiClient, "get")
      .mockReturnValueOnce(new Promise<Attachment[]>((resolve) => (resolveFirst = resolve)))
      .mockResolvedValueOnce([anAttachment({ id: "att-2", conversationId: "c-2" })]);

    const { result, rerender } = renderHook(({ id }) => useAttachments(id), {
      initialProps: { id: "c-1" },
    });
    rerender({ id: "c-2" });
    await waitFor(() => expect(result.current.attachments[0]?.id).toBe("att-2"));

    await act(async () => resolveFirst([anAttachment()]));

    expect(result.current.attachments.map((a) => a.id)).toEqual(["att-2"]);
  });

  it("clears the list when the conversation is deselected", async () => {
    vi.spyOn(apiClient, "get").mockResolvedValueOnce([anAttachment()]);
    const { result, rerender } = renderHook(({ id }: { id: string | null }) => useAttachments(id), {
      initialProps: { id: "c-1" as string | null },
    });
    await waitFor(() => expect(result.current.attachments).toHaveLength(1));

    rerender({ id: null });

    expect(result.current.attachments).toEqual([]);
  });
});
