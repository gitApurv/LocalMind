export type AttachmentStatus = "PROCESSING" | "READY" | "FAILED";

/** Mirrors the backend `AttachmentResponse`. */
export interface Attachment {
  id: string;
  conversationId: string;
  fileName: string;
  fileType?: string;
  fileSize?: number;
  status: AttachmentStatus;
  errorMessage?: string | null;
  createdAt?: string;
}
