"use client";

import { Brain, FileText, Terminal, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { AttachmentUploader } from "@/features/attachments/components/attachment-uploader";
import { ToolAuditPanel } from "@/features/chat/components/tool-audit-panel";
import { MemoryVaultPanel } from "@/features/memories/components/memory-vault-panel";

export type InspectorTab = "docs" | "memory" | "tools";

export interface ContextInspectorProps {
  isOpen: boolean;
  activeTab: InspectorTab;
  onTabChange: (tab: InspectorTab) => void;
  onClose: () => void;
  conversationId: string | null;
  focusedToolName?: string | null;
}

export function ContextInspector({
  isOpen,
  activeTab,
  onTabChange,
  onClose,
  conversationId,
  focusedToolName,
}: ContextInspectorProps) {
  if (!isOpen) return null;

  return (
    <aside
      aria-label="Contextual Inspector"
      className="fixed md:relative inset-y-0 md:inset-auto right-0 z-40 w-full sm:w-96 md:w-96 h-screen md:h-full self-stretch border-l border-border/80 bg-card/95 backdrop-blur-xl flex flex-col shrink-0 shadow-2xl md:shadow-none transition-all"
    >
      {/* Top Tab Bar Header */}
      <div className="h-14 border-b border-border/80 px-4 flex items-center justify-between shrink-0 bg-card/80">
        <div role="tablist" className="flex items-center gap-1">
          <button
            role="tab"
            type="button"
            aria-selected={activeTab === "docs"}
            aria-label="Context Docs tab"
            onClick={() => onTabChange("docs")}
            className={cn(
              "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer",
              activeTab === "docs"
                ? "bg-primary/15 text-primary border border-primary/30 shadow-xs"
                : "text-muted-foreground hover:text-foreground hover:bg-muted/40 border border-transparent"
            )}
          >
            <FileText className="w-3.5 h-3.5" />
            <span>Docs</span>
          </button>

          <button
            role="tab"
            type="button"
            aria-selected={activeTab === "memory"}
            aria-label="Memory Vault tab"
            onClick={() => onTabChange("memory")}
            className={cn(
              "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer",
              activeTab === "memory"
                ? "bg-primary/15 text-primary border border-primary/30 shadow-xs"
                : "text-muted-foreground hover:text-foreground hover:bg-muted/40 border border-transparent"
            )}
          >
            <Brain className="w-3.5 h-3.5" />
            <span>Memory</span>
          </button>

          <button
            role="tab"
            type="button"
            aria-selected={activeTab === "tools"}
            aria-label="Tool Audit tab"
            onClick={() => onTabChange("tools")}
            className={cn(
              "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer",
              activeTab === "tools"
                ? "bg-primary/15 text-primary border border-primary/30 shadow-xs"
                : "text-muted-foreground hover:text-foreground hover:bg-muted/40 border border-transparent"
            )}
          >
            <Terminal className="w-3.5 h-3.5" />
            <span>Tools</span>
          </button>
        </div>

        <Button
          variant="ghost"
          size="icon"
          onClick={onClose}
          aria-label="Close inspector"
          className="w-8 h-8 rounded-lg text-muted-foreground hover:text-foreground"
        >
          <X className="w-4 h-4" />
        </Button>
      </div>

      {/* Tab Content Panel */}
      <div className="flex-1 overflow-y-auto min-h-0 p-4">
        {activeTab === "docs" && <AttachmentUploader conversationId={conversationId} />}
        {activeTab === "memory" && <MemoryVaultPanel />}
        {activeTab === "tools" && <ToolAuditPanel focusedToolName={focusedToolName} />}
      </div>
    </aside>
  );
}
