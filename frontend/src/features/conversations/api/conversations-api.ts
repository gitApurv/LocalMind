import { apiClient } from "@/lib/api-client";
import type { Conversation, CreateConversationRequest, UpdateConversationRequest } from "../types";

export const conversationsApi = {
  list: () => apiClient.get<Conversation[]>("/api/conversations"),
  create: (request: CreateConversationRequest) =>
    apiClient.post<Conversation>("/api/conversations", request),
  update: (id: string, request: UpdateConversationRequest) =>
    apiClient.patch<Conversation>(`/api/conversations/${id}`, request),
  remove: (id: string) => apiClient.delete<void>(`/api/conversations/${id}`),
};
