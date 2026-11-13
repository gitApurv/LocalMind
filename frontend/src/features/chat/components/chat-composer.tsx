"use client";

import * as React from "react";
import { Send, Square } from "lucide-react";
import { Alert } from "@/components/ui/alert";
import { COMPOSER_MAX_HEIGHT_PX } from "../constants";

export interface ChatComposerProps {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  onStop: () => void;
  isStreaming: boolean;
  error: string | null;
  inputRef: React.RefObject<HTMLTextAreaElement | null>;
}

/** Auto-growing message box; Enter sends, Shift+Enter adds a line. */
export function ChatComposer({
  value,
  onChange,
  onSend,
  onStop,
  isStreaming,
  error,
  inputRef,
}: ChatComposerProps) {
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

  return (
    <footer className="p-4 border-t border-border bg-card/60">
      <div className="max-w-3xl mx-auto space-y-2">
        {error && <Alert size="sm">{error}</Alert>}

        <div className="relative flex items-end rounded-2xl border border-border bg-card shadow-sm focus-within:border-primary/60 focus-within:ring-1 focus-within:ring-primary/40 transition-all">
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
          <span>Air-gapped & private • Local Ollama inference</span>
          <span>Shift + Enter for new line</span>
        </div>
      </div>
    </footer>
  );
}
