import { apiClient } from "@/lib/api-client";
import type { ChatRequest, MessageResponse } from "../types";

export const chatApi = {
  listMessages: (conversationId: string) =>
    apiClient.get<MessageResponse[]>(`/api/conversations/${conversationId}/messages`),
  /** Starts a generation; the response body is a server-sent event stream of `ChatEvent`s. */
  stream: (request: ChatRequest, signal: AbortSignal) =>
    apiClient.stream("/api/chat/stream", request, signal),
};
