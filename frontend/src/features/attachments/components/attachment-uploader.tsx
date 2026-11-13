"use client";

import * as React from "react";
import { AlertCircle, Check, FileText, Loader2, UploadCloud } from "lucide-react";
import { Alert } from "@/components/ui/alert";
import { getErrorMessage } from "@/lib/api-error";
import { cn } from "@/lib/utils";
import { useConversationStore } from "@/features/conversations/store/conversation-store";
import { attachmentsApi } from "../api/attachments-api";
import { useAttachments } from "../hooks/use-attachments";
import { formatBytes } from "../lib/format-bytes";
import type { Attachment, AttachmentStatus } from "../types";

const ACCEPTED_FILE_TYPES = ".pdf,.docx,.txt,.md,.csv,.json";

export interface AttachmentUploaderProps {
  conversationId: string | null;
}

/** Drop zone that ingests documents into the conversation's vector store, plus their status. */
export function AttachmentUploader({ conversationId }: AttachmentUploaderProps) {
  const { attachments, refresh } = useAttachments(conversationId);
  const [isUploading, setIsUploading] = React.useState(false);
  const [isDragging, setIsDragging] = React.useState(false);
  const [uploadError, setUploadError] = React.useState<string | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);

  const upload = async (files: FileList | null) => {
    const file = files?.[0];
    if (!file) return;

    setIsUploading(true);
    setUploadError(null);
    try {
      // Uploading into a blank thread creates the conversation first.
      const targetId =
        conversationId ?? (await useConversationStore.getState().createConversation()).id;
      await attachmentsApi.upload(targetId, file);
      // A new conversation changes the prop, which reloads the list by itself.
      if (targetId === conversationId) {
        await refresh();
      }
      if (fileInputRef.current) fileInputRef.current.value = "";
    } catch (error) {
      setUploadError(getErrorMessage(error, "Failed to upload file"));
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div className="space-y-3">
      <div
        onDragOver={(event) => {
          event.preventDefault();
          setIsDragging(true);
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={(event) => {
          event.preventDefault();
          setIsDragging(false);
          void upload(event.dataTransfer.files);
        }}
        onClick={() => fileInputRef.current?.click()}
        className={cn(
          "relative border-2 border-dashed rounded-xl p-4 text-center transition-colors cursor-pointer",
          isDragging
            ? "border-primary bg-primary/10"
            : "border-border/80 bg-card/40 hover:border-primary/50 hover:bg-card/70"
        )}
      >
        <input
          ref={fileInputRef}
          id="attachment-file-input"
          aria-label="Upload document"
          type="file"
          accept={ACCEPTED_FILE_TYPES}
          onChange={(event) => void upload(event.target.files)}
          className="hidden"
        />
        <div className="flex flex-col items-center justify-center space-y-1.5">
          {isUploading ? (
            <Loader2 className="w-6 h-6 text-primary animate-spin" />
          ) : (
            <UploadCloud className="w-6 h-6 text-muted-foreground" />
          )}
          <p className="text-xs font-medium text-foreground">
            {isUploading
              ? "Ingesting document into vector store..."
              : "Drag & drop files here, or click to browse"}
          </p>
          <p className="text-[10px] text-muted-foreground">
            Supports PDF, DOCX, TXT, MD, CSV (chunked & embedded in Qdrant)
          </p>
        </div>
      </div>

      {uploadError && <Alert size="sm">{uploadError}</Alert>}

      {attachments.length > 0 && (
        <div className="space-y-1.5">
          <div className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider px-1">
            Active Context Documents ({attachments.length})
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-48 overflow-y-auto p-1">
            {attachments.map((attachment) => (
              <AttachmentRow key={attachment.id} attachment={attachment} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function AttachmentRow({ attachment }: { attachment: Attachment }) {
  return (
    <div className="flex items-center justify-between p-2 rounded-lg border border-border bg-card/70 text-xs shadow-sm">
      <div className="flex items-center gap-2 truncate pr-2">
        <FileText className="w-4 h-4 text-primary shrink-0" />
        <div className="truncate">
          <p className="font-medium truncate text-foreground text-xs">
            {attachment.fileName || "Unnamed Document"}
          </p>
          <p className="text-[10px] text-muted-foreground">{formatBytes(attachment.fileSize)}</p>
        </div>
      </div>
      <div className="shrink-0 flex items-center gap-1.5">
        <StatusChip status={attachment.status} />
      </div>
    </div>
  );
}

const STATUS_CHIPS: Record<
  AttachmentStatus,
  { className: string; icon: React.ComponentType<{ className?: string }> }
> = {
  READY: { className: "bg-emerald-500/10 text-emerald-400 border-emerald-500/20", icon: Check },
  PROCESSING: {
    className: "bg-amber-500/10 text-amber-300 border-amber-500/20 animate-pulse",
    icon: Loader2,
  },
  FAILED: {
    className: "bg-destructive/10 text-destructive border-destructive/20",
    icon: AlertCircle,
  },
};

function StatusChip({ status }: { status: AttachmentStatus }) {
  const chip = STATUS_CHIPS[status];
  if (!chip) return null;
  const Icon = chip.icon;
  return (
    <span
      className={cn(
        "flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold border",
        chip.className
      )}
    >
      <Icon className={cn("w-2.5 h-2.5", status === "PROCESSING" && "animate-spin")} />
      {status}
    </span>
  );
}
