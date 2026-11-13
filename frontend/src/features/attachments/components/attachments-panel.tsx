import { FileText, X } from "lucide-react";
import { AttachmentUploader } from "./attachment-uploader";

export interface AttachmentsPanelProps {
  conversationId: string | null;
  onClose: () => void;
}

/** Collapsible drawer under the chat header for managing context documents. */
export function AttachmentsPanel({ conversationId, onClose }: AttachmentsPanelProps) {
  return (
    <div className="border-b border-border bg-card/90 p-4 transition-all animate-in slide-in-from-top-2">
      <div className="flex items-center justify-between mb-2">
        <h3 className="text-xs font-semibold text-foreground flex items-center gap-1.5">
          <FileText className="w-3.5 h-3.5 text-primary" />
          Document RAG Management
        </h3>
        <button
          type="button"
          onClick={onClose}
          className="p-1 text-muted-foreground hover:text-foreground"
          aria-label="Close attachment drawer"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
      <AttachmentUploader conversationId={conversationId} />
    </div>
  );
}
