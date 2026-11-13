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
};
