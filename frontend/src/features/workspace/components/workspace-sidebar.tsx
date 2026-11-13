"use client";

import { Bot, Plus, ShieldCheck, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ConversationList } from "@/features/conversations/components/conversation-list";
import { useConversationStore } from "@/features/conversations/store/conversation-store";
import { UserMenu } from "./user-menu";

export interface WorkspaceSidebarProps {
  /** On small screens the sidebar is an overlay that is shown only when open. */
  isOpen: boolean;
  onClose: () => void;
  onOpenSettings: () => void;
  onSignOut: () => void;
}

export function WorkspaceSidebar({
  isOpen,
  onClose,
  onOpenSettings,
  onSignOut,
}: WorkspaceSidebarProps) {
  const createConversation = useConversationStore((s) => s.createConversation);

  const handleNewChat = async () => {
    try {
      await createConversation();
      onClose();
    } catch (error) {
      console.error("Failed to start new chat:", error);
    }
  };

  return (
    <aside
      className={cn(
        isOpen ? "fixed inset-y-0 left-0 z-50 flex shadow-2xl" : "hidden",
        "md:relative md:flex w-72 border-r border-border bg-card/95 backdrop-blur flex-col shrink-0 transition-transform"
      )}
    >
      <div className="p-4 border-b border-border space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-primary text-primary-foreground flex items-center justify-center font-bold">
              <Bot className="w-5 h-5" />
            </div>
            <div>
              <h1 className="font-semibold text-sm tracking-tight text-foreground">LocalMind</h1>
              <span className="text-[10px] text-muted-foreground font-mono">Sovereign AI</span>
            </div>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="flex items-center text-xs text-emerald-400 gap-1 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20">
              <ShieldCheck className="w-3 h-3" />
              <span className="hidden sm:inline">Air-Gapped</span>
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="Close sidebar"
              className="md:hidden p-1 text-muted-foreground hover:text-foreground"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        <Button
          onClick={() => void handleNewChat()}
          className="w-full justify-start gap-2 shadow-sm"
          size="sm"
        >
          <Plus className="w-4 h-4" />
          New Thread
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto p-2">
        <ConversationList />
      </div>

      <UserMenu onOpenSettings={onOpenSettings} onSignOut={onSignOut} />
    </aside>
  );
}
