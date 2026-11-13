import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { AttachmentUploader } from "../attachment-uploader";
import { apiClient } from "@/lib/api-client";
import { resetWorkspaceStores } from "@/test/factories";

describe("AttachmentUploader", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    resetWorkspaceStores();
  });

  it("loads and displays attachments with status chips", async () => {
    const mockAttachments = [
      {
        id: "att-1",
        conversationId: "c-1",
        fileName: "backend_spec.pdf",
        fileType: "application/pdf",
        fileSize: 10240,
        status: "READY" as const,
        createdAt: "2026-01-01T00:00:00Z",
      },
      {
        id: "att-2",
        conversationId: "c-1",
        fileName: "legacy_notes.txt",
        fileType: "text/plain",
        fileSize: 2048,
        status: "PROCESSING" as const,
        createdAt: "2026-01-01T00:00:05Z",
      },
    ];

    vi.spyOn(apiClient, "get").mockResolvedValueOnce(mockAttachments);

    render(<AttachmentUploader conversationId="c-1" />);

    await waitFor(() => {
      expect(screen.getByText("backend_spec.pdf")).toBeDefined();
      expect(screen.getByText("legacy_notes.txt")).toBeDefined();
    });

    expect(screen.getByText("READY")).toBeDefined();
    expect(screen.getByText("PROCESSING")).toBeDefined();
  });

  it("handles file upload successfully when conversationId is present", async () => {
    vi.spyOn(apiClient, "get").mockResolvedValueOnce([]);
    vi.spyOn(apiClient, "post").mockResolvedValueOnce({
      id: "att-new",
      conversationId: "c-1",
      fileName: "test.pdf",
      status: "PROCESSING",
    });
    // Follow-up get
    vi.spyOn(apiClient, "get").mockResolvedValueOnce([
      {
        id: "att-new",
        conversationId: "c-1",
        fileName: "test.pdf",
        status: "PROCESSING",
        fileSize: 5000,
      },
    ]);

    render(<AttachmentUploader conversationId="c-1" />);

    const file = new File(["dummy content"], "test.pdf", { type: "application/pdf" });
    const input = screen.getByLabelText(/upload document/i) as HTMLInputElement;

    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalled();
    });
  });

  it("auto-creates conversation on file upload when conversationId is null", async () => {
    const newConv = {
      id: "c-auto-created",
      userId: "u-1",
      title: "New Chat",
      modelName: "gemma3:1b",
      pinned: false,
      createdAt: "",
      updatedAt: "",
    };

    vi.spyOn(apiClient, "post").mockResolvedValueOnce(newConv); // create conversation
    vi.spyOn(apiClient, "post").mockResolvedValueOnce({
      id: "att-auto",
      conversationId: "c-auto-created",
      fileName: "auto.pdf",
      status: "PROCESSING",
    }); // upload attachment

    render(<AttachmentUploader conversationId={null} />);

    const file = new File(["dummy content"], "auto.pdf", { type: "application/pdf" });
    const input = screen.getByLabelText(/upload document/i) as HTMLInputElement;

    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledTimes(2);
    });
  });
});
