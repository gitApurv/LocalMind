import { apiClient } from "@/lib/api-client";
import type { Attachment } from "../types";

export const attachmentsApi = {
  list: (conversationId: string) =>
    apiClient.get<Attachment[]>(`/api/conversations/${conversationId}/attachments`),
  upload: (conversationId: string, file: File) => {
    const formData = new FormData();
    formData.append("file", file);
    return apiClient.post<unknown>(`/api/conversations/${conversationId}/attachments`, formData);
  },
  download: async (
    conversationId: string,
    attachmentId: string,
    fileName: string
  ): Promise<void> => {
    const blob = await apiClient.blob(
      `/api/conversations/${conversationId}/attachments/${attachmentId}/download`
    );
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  },
};
