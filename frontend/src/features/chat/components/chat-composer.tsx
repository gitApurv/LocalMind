"use client";

import * as React from "react";
import { createPortal } from "react-dom";
import { Bot, Code2, FileText, Send, Sparkles, Square, UserCog } from "lucide-react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { COMPOSER_MAX_HEIGHT_PX, PERSONA_PRESETS } from "../constants";
import { useChatStore } from "../store/chat-store";

export interface ChatComposerProps {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  onStop: () => void;
  isStreaming: boolean;
  error: string | null;
  inputRef: React.RefObject<HTMLTextAreaElement | null>;
  onToggleAttachments?: () => void;
}

const PERSONA_ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
  default: Bot,
  code_architect: Code2,
  researcher: FileText,
  custom: UserCog,
};

/** Auto-growing message box with per-conversation persona selector chips. */
export function ChatComposer({
  value,
  onChange,
  onSend,
  onStop,
  isStreaming,
  error,
  inputRef,
  onToggleAttachments,
}: ChatComposerProps) {
  const activePersonaId = useChatStore((s) => s.activePersonaId);
  const customPersonaPrompt = useChatStore((s) => s.customPersonaPrompt);
  const setActivePersona = useChatStore((s) => s.setActivePersona);

  const [isCustomModalOpen, setIsCustomModalOpen] = React.useState(false);
  const [customDraft, setCustomDraft] = React.useState(customPersonaPrompt);
  const [mounted, setMounted] = React.useState(false);

  React.useEffect(() => {
    setMounted(true);
  }, []);

  React.useLayoutEffect(() => {
    const textarea = inputRef.current;
    if (!textarea) return;
    textarea.style.height = "auto";
    if (value) {
      textarea.style.height = `${Math.min(textarea.scrollHeight, COMPOSER_MAX_HEIGHT_PX)}px`;
    }
  }, [value, inputRef]);

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      onSend();
    }
  };

  const handleSelectPersona = (id: string) => {
    if (id === "custom") {
      setCustomDraft(customPersonaPrompt);
      setIsCustomModalOpen(true);
    } else {
      setActivePersona(id);
    }
  };

  const handleSaveCustom = (e: React.FormEvent) => {
    e.preventDefault();
    setActivePersona("custom", customDraft.trim());
    setIsCustomModalOpen(false);
  };

  return (
    <footer className="p-4 border-t border-border/80 bg-background/80 backdrop-blur-md">
      <div className="max-w-3xl mx-auto space-y-2.5">
        {error && <Alert size="sm">{error}</Alert>}

        {/* Persona Selector Chips */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs">
          <span className="text-[10px] uppercase font-semibold text-muted-foreground mr-1 shrink-0">
            Persona:
          </span>
          {PERSONA_PRESETS.map((preset) => {
            const Icon = PERSONA_ICONS[preset.id] ?? Bot;
            const isSelected = activePersonaId === preset.id;
            return (
              <button
                key={preset.id}
                type="button"
                onClick={() => handleSelectPersona(preset.id)}
                className={cn(
                  "flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-medium border transition-all shrink-0 cursor-pointer",
                  isSelected
                    ? "border-primary/50 bg-primary/15 text-primary shadow-xs shadow-primary/20"
                    : "border-border/70 bg-card/40 text-muted-foreground hover:border-border hover:text-foreground"
                )}
                title={preset.description}
              >
                <Icon className="w-3 h-3" />
                <span>{preset.name}</span>
                {isSelected && preset.id === "custom" && customPersonaPrompt && (
                  <span className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
                )}
              </button>
            );
          })}
        </div>

        {/* Composer Textarea Container */}
        <div className="relative flex items-end rounded-2xl border border-border/80 bg-card/60 shadow-xs focus-within:border-primary/60 focus-within:ring-1 focus-within:ring-primary/40 focus-within:bg-card/90 transition-all">
          <textarea
            ref={inputRef}
            rows={1}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Message LocalMind (Enter to send, Shift+Enter for newline)..."
            className="w-full resize-none bg-transparent px-4 py-3 text-xs sm:text-sm text-foreground placeholder:text-muted-foreground focus:outline-none max-h-44 min-h-[44px]"
          />
          <div className="flex items-center gap-1.5 p-2 shrink-0">
            {onToggleAttachments && (
              <button
                type="button"
                onClick={onToggleAttachments}
                aria-label="Attach documents"
                title="Attach documents"
                className="w-8 h-8 rounded-xl border border-border/70 bg-card/40 hover:bg-muted text-muted-foreground hover:text-foreground flex items-center justify-center transition-colors"
              >
                <FileText className="w-3.5 h-3.5" />
              </button>
            )}

            {isStreaming ? (
              <button
                type="button"
                onClick={onStop}
                aria-label="Stop generation"
                title="Stop generating response"
                className="w-8 h-8 rounded-xl bg-destructive text-destructive-foreground hover:bg-destructive/90 flex items-center justify-center transition-colors shadow-sm"
              >
                <Square className="w-3.5 h-3.5 fill-current" />
              </button>
            ) : (
              <button
                type="button"
                onClick={onSend}
                disabled={!value.trim()}
                aria-label="Send message"
                title="Send message (Enter)"
                className="w-8 h-8 rounded-xl bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-40 disabled:hover:bg-primary flex items-center justify-center transition-colors shadow-sm"
              >
                <Send className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </div>

        <div className="flex items-center justify-between text-[11px] text-muted-foreground px-2">
          <span className="flex items-center gap-1">
            <Sparkles className="w-3 h-3 text-primary/70" />
            Air-gapped & sovereign • Local Ollama inference
          </span>
          <span>Shift + Enter for new line</span>
        </div>
      </div>

      {/* Custom Persona Modal */}
      {isCustomModalOpen &&
        mounted &&
        createPortal(
          <div
            role="dialog"
            aria-modal="true"
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-150"
            onClick={() => setIsCustomModalOpen(false)}
          >
            <div
              className="w-full max-w-md max-h-[90vh] overflow-y-auto rounded-2xl border border-border bg-card p-5 space-y-4 shadow-2xl text-card-foreground"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="space-y-1">
                <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
                  <UserCog className="w-4 h-4 text-primary" />
                  Custom Conversation Persona
                </h3>
                <p className="text-xs text-muted-foreground">
                  Define the system instructions injected into this conversation thread.
                </p>
              </div>
              <form onSubmit={handleSaveCustom} className="space-y-3">
                <textarea
                  rows={4}
                  value={customDraft}
                  onChange={(e) => setCustomDraft(e.target.value)}
                  placeholder="e.g., You are an expert penetration tester. Analyze code for security vulnerabilities..."
                  className="w-full rounded-xl border border-border bg-background/80 p-3 text-xs text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary/40 font-mono"
                />
                <div className="flex items-center justify-end gap-2">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => setIsCustomModalOpen(false)}
                  >
                    Cancel
                  </Button>
                  <Button type="submit" size="sm" variant="glow">
                    Apply Persona
                  </Button>
                </div>
              </form>
            </div>
          </div>,
          document.body
        )}
    </footer>
  );
}
