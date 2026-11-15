"use client";

import * as React from "react";
import { Brain, Loader2, Pin, Search, X } from "lucide-react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useMemoryStore } from "../store/memory-store";
import { MemoryCard } from "./memory-card";

export interface MemoryVaultPanelProps {
  onClose?: () => void;
}

export function MemoryVaultPanel({ onClose }: MemoryVaultPanelProps) {
  const memories = useMemoryStore((s) => s.memories);
  const isLoading = useMemoryStore((s) => s.isLoading);
  const error = useMemoryStore((s) => s.error);
  const fetchMemories = useMemoryStore((s) => s.fetchMemories);
  const togglePin = useMemoryStore((s) => s.togglePin);
  const deleteMemory = useMemoryStore((s) => s.deleteMemory);

  const [search, setSearch] = React.useState("");

  React.useEffect(() => {
    void fetchMemories();
  }, [fetchMemories]);

  const filtered = React.useMemo(() => {
    if (!search.trim()) return memories;
    const term = search.toLowerCase();
    return memories.filter((m) => m.memoryFact.toLowerCase().includes(term));
  }, [memories, search]);

  const pinned = React.useMemo(() => filtered.filter((m) => m.pinned), [filtered]);
  const unpinned = React.useMemo(() => filtered.filter((m) => !m.pinned), [filtered]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Brain className="w-4 h-4 text-primary" />
          <h3 className="text-xs font-semibold uppercase tracking-wider text-foreground">
            Sovereign Memory Vault
          </h3>
          <span className="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-semibold border border-primary/30 bg-primary/10 text-primary">
            {memories.length}
          </span>
        </div>
        {onClose && (
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            aria-label="Close memory vault"
            className="w-7 h-7 text-muted-foreground hover:text-foreground"
          >
            <X className="w-4 h-4" />
          </Button>
        )}
      </div>

      <div className="relative">
        <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Filter memories..."
          className="pl-8 h-8 text-xs bg-card/60 border-border/80"
        />
      </div>

      {error && <Alert size="sm">{error}</Alert>}

      {isLoading && memories.length === 0 ? (
        <div className="flex items-center justify-center p-8 text-muted-foreground text-xs gap-2">
          <Loader2 className="w-4 h-4 animate-spin text-primary" />
          <span>Loading memories...</span>
        </div>
      ) : filtered.length === 0 ? (
        <div className="p-6 text-center rounded-xl border border-dashed border-border/80 bg-card/40 space-y-2">
          <Brain className="w-7 h-7 mx-auto text-muted-foreground opacity-60" />
          <p className="text-xs font-medium text-foreground">No memories found</p>
          <p className="text-[11px] text-muted-foreground max-w-xs mx-auto">
            As you chat, LocalMind automatically saves facts about your preferences, tools, and
            projects into your sovereign vault.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {pinned.length > 0 && (
            <div className="space-y-2">
              <div className="flex items-center gap-1.5 text-[11px] font-semibold text-primary uppercase tracking-wider">
                <Pin className="w-3 h-3 fill-current" />
                <span>Pinned ({pinned.length})</span>
              </div>
              <div className="space-y-2">
                {pinned.map((memory) => (
                  <MemoryCard
                    key={memory.id}
                    memory={memory}
                    onTogglePin={(id) => void togglePin(id)}
                    onDelete={(id) => void deleteMemory(id)}
                  />
                ))}
              </div>
            </div>
          )}

          {unpinned.length > 0 && (
            <div className="space-y-2">
              {pinned.length > 0 && (
                <div className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                  Other Memories ({unpinned.length})
                </div>
              )}
              <div className="space-y-2">
                {unpinned.map((memory) => (
                  <MemoryCard
                    key={memory.id}
                    memory={memory}
                    onTogglePin={(id) => void togglePin(id)}
                    onDelete={(id) => void deleteMemory(id)}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
