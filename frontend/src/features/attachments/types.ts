export type AttachmentStatus = "PROCESSING" | "READY" | "FAILED";

/** Mirrors the backend `AttachmentResponse`. */
export interface Attachment {
  id: string;
  conversationId: string;
  userId?: string;
  fileName: string;
  fileType?: string;
  fileSize?: number;
  status: AttachmentStatus;
  chunkCount?: number | null;
  errorMessage?: string | null;
  createdAt?: string;
}
