import { useCallback, useEffect, useState } from "react";
import { attachmentsApi } from "../api/attachments-api";
import type { Attachment } from "../types";

// Ingestion runs asynchronously on the backend; poll until no document is still processing.
const POLL_INTERVAL_MS = 3000;

interface LoadedAttachments {
  conversationId: string;
  items: Attachment[];
}

/** Attachments of a conversation, refreshed while any of them is still being ingested. */
export function useAttachments(conversationId: string | null) {
  // Keyed by conversation: a switch shows nothing until the new list arrives, and a late
  // response for the previous conversation is never shown.
  const [loaded, setLoaded] = useState<LoadedAttachments | null>(null);
  const attachments =
    conversationId && loaded?.conversationId === conversationId ? loaded.items : [];

  const refresh = useCallback(async () => {
    if (!conversationId) return;
    const items = await loadAttachments(conversationId);
    if (items) setLoaded({ conversationId, items });
  }, [conversationId]);

  useEffect(() => {
    if (!conversationId) return;
    let cancelled = false;
    void loadAttachments(conversationId).then((items) => {
      if (items && !cancelled) setLoaded({ conversationId, items });
    });
    return () => {
      cancelled = true;
    };
  }, [conversationId]);

  const isProcessing = attachments.some((a) => a.status === "PROCESSING");
  useEffect(() => {
    if (!isProcessing || !conversationId) return;
    const timer = setInterval(() => void refresh(), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [isProcessing, conversationId, refresh]);

  return { attachments, refresh };
}

/** The conversation's attachments, or `null` when loading failed (the caller keeps its list). */
async function loadAttachments(conversationId: string): Promise<Attachment[] | null> {
  try {
    return (await attachmentsApi.list(conversationId)) ?? [];
  } catch (error) {
    console.error("Failed to load attachments:", error);
    return null;
  }
}
