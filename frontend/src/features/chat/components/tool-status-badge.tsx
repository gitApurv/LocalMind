import {
  Bookmark,
  Brain,
  Calculator,
  Check,
  Clock,
  Compass,
  FileSearch,
  Globe,
  Loader2,
  Wrench,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";

export interface ToolStatusBadgeProps {
  tool: string;
  status?: "executing" | "completed";
}

interface ToolDisplay {
  label: string;
  icon: LucideIcon;
}

/** Display names for the backend's LLM-callable tools (`tools` package), keyed by method name. */
const TOOL_DISPLAY: Record<string, ToolDisplay> = {
  searchWeb: { label: "Web Search", icon: Globe },
  calculateExpression: { label: "Math Calculator", icon: Calculator },
  searchUploadedDocs: { label: "Document Knowledge", icon: FileSearch },
  getUserMemories: { label: "Memory Recall", icon: Brain },
  saveUserMemory: { label: "Memory Storage", icon: Bookmark },
  scrapeWebPage: { label: "Web Scraper", icon: Compass },
  getCurrentDateTime: { label: "Date & Time", icon: Clock },
};

export function ToolStatusBadge({ tool, status = "completed" }: ToolStatusBadgeProps) {
  const { label, icon: ToolIcon } = TOOL_DISPLAY[tool] ?? { label: tool, icon: Wrench };
  const isExecuting = status === "executing";

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        "inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-medium border transition-all",
        isExecuting
          ? "border-amber-500/30 bg-amber-500/10 text-amber-300 animate-pulse shadow-sm"
          : "border-primary/20 bg-primary/5 text-primary/80"
      )}
    >
      <ToolIcon className="w-3 h-3 shrink-0" />
      <span>{isExecuting ? `Running ${label}...` : `Used ${label}`}</span>
      {isExecuting ? (
        <Loader2 className="w-2.5 h-2.5 animate-spin text-amber-400" />
      ) : (
        <Check className="w-2.5 h-2.5 text-emerald-400" />
      )}
    </div>
  );
}
