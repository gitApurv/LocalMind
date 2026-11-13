"use client";

import * as React from "react";
import { Brain, ChevronDown, Sparkles } from "lucide-react";

export interface ReasoningAccordionProps {
  content: string;
  durationSec?: number | null;
  isStreaming?: boolean;
}

/** Collapsible "thinking" trace of reasoning models; opens itself while reasoning streams. */
export function ReasoningAccordion({
  content,
  durationSec,
  isStreaming = false,
}: ReasoningAccordionProps) {
  const [isOpen, setIsOpen] = React.useState(isStreaming);

  React.useEffect(() => {
    if (isStreaming) {
      setIsOpen(true);
    }
  }, [isStreaming]);

  if (!content && !isStreaming) return null;

  return (
    <details
      className="group border border-border/60 bg-muted/20 rounded-lg overflow-hidden my-2 text-xs transition-all"
      open={isOpen}
      onToggle={(event) => setIsOpen(event.currentTarget.open)}
    >
      <summary className="flex items-center justify-between px-3 py-2 cursor-pointer select-none text-muted-foreground hover:text-foreground hover:bg-muted/40 transition-colors list-none [&::-webkit-details-marker]:hidden">
        <div className="flex items-center gap-2">
          {isStreaming ? (
            <Sparkles className="w-3.5 h-3.5 text-primary animate-pulse" />
          ) : (
            <Brain className="w-3.5 h-3.5 text-muted-foreground" />
          )}
          <span className="font-medium text-[11px] tracking-wide">
            {summaryLabel(isStreaming, durationSec)}
          </span>
        </div>
        <ChevronDown className="w-3.5 h-3.5 text-muted-foreground transition-transform duration-200 group-open:rotate-180" />
      </summary>
      <div className="px-3 py-2.5 border-t border-border/40 font-mono text-[11px] leading-relaxed text-muted-foreground/90 whitespace-pre-wrap max-h-64 overflow-y-auto bg-card/40">
        {content || (isStreaming ? "Formulating response..." : "")}
      </div>
    </details>
  );
}

function summaryLabel(isStreaming: boolean, durationSec?: number | null): string {
  if (isStreaming) return "Thinking...";
  if (durationSec !== undefined && durationSec !== null) return `Thought for ${durationSec}s`;
  return "Thought process";
}
