"use client";

import { Bot, Menu, PanelRight, Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";
import { useConversationStore } from "@/features/conversations/store/conversation-store";
import { ModelSelector } from "@/features/models/components/model-selector";
import { useChatStore } from "../store/chat-store";

export type InspectorTabType = "docs" | "memory" | "tools";

export interface ChatHeaderProps {
  isInspectorOpen?: boolean;
  activeInspectorTab?: InspectorTabType;
  onOpenTab?: (tab: InspectorTabType) => void;
  onToggleInspector?: () => void;
  onToggleSidebar?: () => void;
  // Backwards-compatible aliases
  isAttachmentsOpen?: boolean;
  onToggleAttachments?: () => void;
}

export function ChatHeader({
  isInspectorOpen,
  onToggleInspector,
  onToggleSidebar,
  isAttachmentsOpen,
}: ChatHeaderProps) {
  const title = useConversationStore(
    (s) => s.conversations.find((c) => c.id === s.activeConversationId)?.title
  );
  const isStreaming = useChatStore((s) => s.isStreaming);

  const effectiveIsOpen = isInspectorOpen ?? isAttachmentsOpen ?? false;

  return (
    <header className="h-14 border-b border-border/80 bg-card/60 backdrop-blur-md px-4 flex items-center justify-between shrink-0 z-10">
      <div className="flex items-center gap-2.5 truncate">
        {onToggleSidebar && (
          <button
            type="button"
            onClick={onToggleSidebar}
            aria-label="Toggle navigation sidebar"
            className="md:hidden p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted cursor-pointer"
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
        <ModelSelector />

        {/* Inspector Toggle Button */}
        {onToggleInspector && (
          <button
            type="button"
            onClick={onToggleInspector}
            aria-label="Toggle inspector drawer"
            title={effectiveIsOpen ? "Close inspector" : "Open inspector"}
            className={cn(
              "p-2 rounded-lg border transition-colors shadow-xs cursor-pointer",
              effectiveIsOpen
                ? "border-primary/50 bg-primary/15 text-primary"
                : "border-border/80 bg-card/60 text-muted-foreground hover:bg-card/90 hover:text-foreground"
            )}
          >
            <PanelRight className="w-4 h-4" />
          </button>
        )}
      </div>
    </header>
  );
}
