import { apiClient } from "@/lib/api-client";
import type { Memory, UpdateMemoryRequest } from "../types";

export const memoriesApi = {
  list: () => apiClient.get<Memory[]>("/api/memories"),
  update: (id: string, req: UpdateMemoryRequest) =>
    apiClient.patch<Memory>(`/api/memories/${id}`, req),
  delete: (id: string) => apiClient.delete<void>(`/api/memories/${id}`),
};
