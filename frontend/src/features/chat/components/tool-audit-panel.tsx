"use client";

import * as React from "react";
import {
  Bookmark,
  Brain,
  Calculator,
  Check,
  Clock,
  Compass,
  FileSearch,
  Globe,
  Terminal,
  Wrench,
  X,
  type LucideIcon,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { useChatStore } from "../store/chat-store";
import type { ToolCallRecord } from "../types";

export interface ToolAuditPanelProps {
  onClose?: () => void;
  focusedToolName?: string | null;
}

const TOOL_ICONS: Record<string, { label: string; icon: LucideIcon }> = {
  searchWeb: { label: "SearXNG Web Search", icon: Globe },
  calculateExpression: { label: "Math Evaluator (exp4j)", icon: Calculator },
  searchUploadedDocs: { label: "Document RAG (Qdrant)", icon: FileSearch },
  getUserMemories: { label: "Recall Long-Term Memory", icon: Brain },
  saveUserMemory: { label: "Save Long-Term Memory", icon: Bookmark },
  scrapeWebPage: { label: "Web Page Reader (SSRF-Guarded)", icon: Compass },
  getCurrentDateTime: { label: "Date & Time Engine", icon: Clock },
};

export function ToolAuditPanel({ onClose, focusedToolName }: ToolAuditPanelProps) {
  const messages = useChatStore((s) => s.messages);

  // Aggregate all tool calls across messages in chronological order
  const allToolCalls = React.useMemo(() => {
    const list: Array<ToolCallRecord & { messageId: string }> = [];
    messages.forEach((m) => {
      if (m.toolCallsData && m.toolCallsData.length > 0) {
        m.toolCallsData.forEach((tc) => {
          list.push({ ...tc, messageId: m.id });
        });
      }
    });
    return list;
  }, [messages]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Terminal className="w-4 h-4 text-primary" />
          <h3 className="text-xs font-semibold uppercase tracking-wider text-foreground">
            Tool Execution Audit
          </h3>
          <span className="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-semibold border border-primary/30 bg-primary/10 text-primary">
            {allToolCalls.length}
          </span>
        </div>
        {onClose && (
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            aria-label="Close tool audit"
            className="w-7 h-7 text-muted-foreground hover:text-foreground"
          >
            <X className="w-4 h-4" />
          </Button>
        )}
      </div>

      {allToolCalls.length === 0 ? (
        <div className="p-6 text-center rounded-xl border border-dashed border-border/80 bg-card/40 space-y-2">
          <Terminal className="w-7 h-7 mx-auto text-muted-foreground opacity-60" />
          <p className="text-xs font-medium text-foreground">No tool executions recorded</p>
          <p className="text-[11px] text-muted-foreground max-w-xs mx-auto">
            When tool-capable models (e.g. Qwen 2.5) invoke SearXNG, calculate math, read web pages,
            or search documents, full execution logs and arguments appear here.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {allToolCalls.map((call, idx) => {
            const config = TOOL_ICONS[call.tool] ?? { label: call.tool, icon: Wrench };
            const Icon = config.icon;
            const isHighlighted = focusedToolName === call.tool;

            return (
              <div
                key={`${call.tool}-${idx}`}
                className={`p-3 rounded-xl border text-xs space-y-2 transition-all shadow-xs ${
                  isHighlighted
                    ? "border-primary/60 bg-primary/10 ring-1 ring-primary/40"
                    : "border-border/80 bg-card/60 hover:bg-card/90"
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 font-medium text-foreground">
                    <Icon className="w-3.5 h-3.5 text-primary shrink-0" />
                    <span>{config.label}</span>
                  </div>
                  <div className="flex items-center gap-1.5 text-[10px] text-muted-foreground">
                    <span className="flex items-center gap-1 text-emerald-400 font-medium">
                      <Check className="w-2.5 h-2.5" />
                      Executed
                    </span>
                    {call.timestamp && <span>{formatTime(call.timestamp)}</span>}
                  </div>
                </div>

                {call.input && (
                  <div className="space-y-1">
                    <span className="text-[10px] uppercase font-semibold text-muted-foreground tracking-wider">
                      Input Arguments
                    </span>
                    <pre className="p-2 rounded-lg bg-background/80 border border-border/60 text-[11px] font-mono text-muted-foreground overflow-x-auto whitespace-pre-wrap break-all">
                      {formatInput(call.input)}
                    </pre>
                  </div>
                )}

                {call.resultSummary && (
                  <div className="space-y-1">
                    <span className="text-[10px] uppercase font-semibold text-muted-foreground tracking-wider">
                      Result Summary
                    </span>
                    <div className="text-[11px] font-mono text-foreground/90 bg-muted/20 p-2.5 rounded-lg border border-border/40 whitespace-pre-wrap break-words leading-relaxed">
                      {formatResultSummary(call.resultSummary)}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function formatInput(raw: string): string {
  try {
    const parsed = JSON.parse(raw);
    return JSON.stringify(parsed, null, 2);
  } catch {
    return raw;
  }
}

export function formatResultSummary(raw: string): string {
  let cleaned = raw.trim();
  if (cleaned.startsWith('"') && cleaned.endsWith('"')) {
    try {
      const parsed = JSON.parse(cleaned);
      if (typeof parsed === "string") cleaned = parsed;
    } catch {
      cleaned = cleaned.slice(1, -1);
    }
  }
  return cleaned.replace(/\\r\\n/g, "\n").replace(/\\n/g, "\n");
}

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  } catch {
    return "";
  }
}
