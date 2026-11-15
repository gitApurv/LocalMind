import { Pin, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { Memory } from "../types";

export interface MemoryCardProps {
  memory: Memory;
  onTogglePin: (id: string) => void;
  onDelete: (id: string) => void;
}

export function MemoryCard({ memory, onTogglePin, onDelete }: MemoryCardProps) {
  return (
    <div
      className={cn(
        "group relative flex items-start justify-between gap-3 p-3 rounded-xl border text-xs transition-all shadow-xs",
        memory.pinned
          ? "border-primary/40 bg-primary/5 hover:border-primary/60"
          : "border-border/80 bg-card/60 hover:border-border hover:bg-card/90"
      )}
    >
      <div className="flex-1 min-w-0 space-y-1">
        <p className="text-foreground leading-relaxed break-words font-normal">
          {memory.memoryFact}
        </p>
        <div className="flex items-center gap-2 text-[10px] text-muted-foreground">
          {memory.pinned && (
            <span className="flex items-center gap-1 text-primary font-semibold">
              <Pin className="w-2.5 h-2.5 fill-current" />
              Pinned
            </span>
          )}
          <span>{formatDate(memory.createdAt)}</span>
        </div>
      </div>

      <div className="flex items-center gap-1 shrink-0 opacity-80 group-hover:opacity-100 transition-opacity">
        <Button
          variant="ghost"
          size="icon"
          aria-label={memory.pinned ? "Unpin memory" : "Pin memory"}
          title={memory.pinned ? "Unpin memory" : "Pin memory"}
          onClick={() => onTogglePin(memory.id)}
          className={cn(
            "w-7 h-7 rounded-lg",
            memory.pinned
              ? "text-primary hover:text-primary hover:bg-primary/10"
              : "text-muted-foreground hover:text-foreground"
          )}
        >
          <Pin className={cn("w-3.5 h-3.5", memory.pinned && "fill-current")} />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          aria-label="Delete memory"
          title="Delete memory"
          onClick={() => onDelete(memory.id)}
          className="w-7 h-7 rounded-lg text-muted-foreground hover:text-destructive hover:bg-destructive/10"
        >
          <Trash2 className="w-3.5 h-3.5" />
        </Button>
      </div>
    </div>
  );
}

function formatDate(isoDate: string): string {
  try {
    return new Date(isoDate).toLocaleDateString([], {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  } catch {
    return "";
  }
}
