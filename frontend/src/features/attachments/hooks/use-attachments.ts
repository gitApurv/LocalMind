import { useCallback, useEffect, useState } from "react";
import { attachmentsApi } from "../api/attachments-api";
import type { Attachment } from "../types";

// Ingestion runs asynchronously on the backend; poll until no document is still processing.
const POLL_INTERVAL_MS = 3000;

/** Attachments of a conversation, refreshed while any of them is still being ingested. */
export function useAttachments(conversationId: string | null) {
  const [attachments, setAttachments] = useState<Attachment[]>([]);

  const refresh = useCallback(async () => {
    if (!conversationId) {
      setAttachments([]);
      return;
    }
    try {
      setAttachments((await attachmentsApi.list(conversationId)) ?? []);
    } catch (error) {
      console.error("Failed to load attachments:", error);
    }
  }, [conversationId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const isProcessing = attachments.some((a) => a.status === "PROCESSING");
  useEffect(() => {
    if (!isProcessing || !conversationId) return;
    const timer = setInterval(() => void refresh(), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [isProcessing, conversationId, refresh]);

  return { attachments, refresh };
}
