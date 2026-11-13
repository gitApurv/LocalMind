import { Bot, Menu, Paperclip, Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";
import { useConversationStore } from "@/features/conversations/store/conversation-store";
import { ModelSelector } from "@/features/models/components/model-selector";
import { useChatStore } from "../store/chat-store";

export interface ChatHeaderProps {
  isAttachmentsOpen: boolean;
  onToggleAttachments: () => void;
  onToggleSidebar?: () => void;
}

export function ChatHeader({
  isAttachmentsOpen,
  onToggleAttachments,
  onToggleSidebar,
}: ChatHeaderProps) {
  const title = useConversationStore(
    (s) => s.conversations.find((c) => c.id === s.activeConversationId)?.title
  );
  const isStreaming = useChatStore((s) => s.isStreaming);

  return (
    <header className="h-14 border-b border-border bg-card/40 px-4 flex items-center justify-between shrink-0">
      <div className="flex items-center gap-2.5 truncate">
        {onToggleSidebar && (
          <button
            type="button"
            onClick={onToggleSidebar}
            aria-label="Toggle navigation sidebar"
            className="md:hidden p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted"
          >
            <Menu className="w-4 h-4" />
          </button>
        )}
        <Bot className="w-4 h-4 text-primary shrink-0" />
        <h2 className="font-medium text-xs sm:text-sm text-foreground truncate">
          {title || "New Thread"}
        </h2>
        {isStreaming && (
          <span className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-primary/10 text-primary border border-primary/20 animate-pulse">
            <Sparkles className="w-2.5 h-2.5" />
            Streaming
          </span>
        )}
      </div>

      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={onToggleAttachments}
          aria-label="Toggle attachments drawer"
          title="Manage conversation attachments"
          className={cn(
            "flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-colors shadow-sm",
            isAttachmentsOpen
              ? "border-primary/40 bg-primary/10 text-primary"
              : "border-border bg-card/60 text-muted-foreground hover:bg-muted hover:text-foreground"
          )}
        >
          <Paperclip className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Context Docs</span>
        </button>
        <ModelSelector />
      </div>
    </header>
  );
}
