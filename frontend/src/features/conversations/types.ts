/** Mirrors the backend `ConversationResponse`. */
export interface Conversation {
  id: string;
  userId: string;
  title: string;
  modelName: string;
  pinned: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateConversationRequest {
  title: string;
  modelName: string;
}

export interface UpdateConversationRequest {
  title?: string;
  pinned?: boolean;
}
