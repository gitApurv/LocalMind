import { describe, expect, it, vi } from "vitest";
import { apiClient } from "@/lib/api-client";
import { attachmentsApi } from "../attachments-api";

describe("attachmentsApi.download", () => {
  it("downloads attachment and triggers browser anchor download", async () => {
    const mockBlob = new Blob(["file content"], { type: "application/pdf" });
    vi.spyOn(apiClient, "blob").mockResolvedValueOnce(mockBlob);

    global.URL.createObjectURL = vi.fn(() => "blob:http://localhost/test-uuid");
    global.URL.revokeObjectURL = vi.fn();

    await expect(attachmentsApi.download("c1", "a1", "sample-spec.pdf")).resolves.not.toThrow();

    expect(apiClient.blob).toHaveBeenCalledWith("/api/conversations/c1/attachments/a1/download");
    expect(global.URL.createObjectURL).toHaveBeenCalledWith(mockBlob);
    expect(global.URL.revokeObjectURL).toHaveBeenCalledWith("blob:http://localhost/test-uuid");
  });
});
