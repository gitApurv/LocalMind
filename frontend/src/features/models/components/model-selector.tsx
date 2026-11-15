"use client";

import * as React from "react";
import { Check, ChevronDown, Cpu, Wrench } from "lucide-react";
import { cn } from "@/lib/utils";
import { buildModelOptions, supportsTools } from "../lib/model-capabilities";
import { useModelStore } from "../store/model-store";
import { useConversationStore } from "@/features/conversations/store/conversation-store";

export function ModelSelector() {
  const selectedModel = useModelStore((s) => s.selectedModel);
  const models = useModelStore((s) => s.models);
  const modelCapabilities = useModelStore((s) => s.modelCapabilities);
  const setSelectedModel = useModelStore((s) => s.setSelectedModel);

  const [isOpen, setIsOpen] = React.useState(false);
  const dropdownRef = React.useRef<HTMLDivElement | null>(null);

  const options = React.useMemo(() => buildModelOptions(models), [models]);
  const selectedOption = options.find((opt) => opt.id === selectedModel);

  React.useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setIsOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  const handleSelect = (nextModel: string) => {
    setSelectedModel(nextModel);
    const activeId = useConversationStore.getState().activeConversationId;
    if (activeId) {
      void useConversationStore.getState().updateConversationModel(activeId, nextModel);
    }
    setIsOpen(false);
  };

  return (
    <div className="flex items-center gap-2">
      <div className="relative flex items-center" ref={dropdownRef}>
        {/* Custom Dropdown Trigger Button */}
        <button
          type="button"
          role="combobox"
          aria-label="Select model"
          aria-expanded={isOpen}
          aria-haspopup="listbox"
          aria-controls="model-menu-listbox"
          onClick={() => setIsOpen((prev) => !prev)}
          className={cn(
            "flex items-center gap-2 px-2.5 py-1.5 rounded-lg border text-xs text-foreground transition-all cursor-pointer shadow-xs",
            isOpen
              ? "border-primary/60 bg-card/90 ring-1 ring-primary/40"
              : "border-border/80 bg-card/60 hover:bg-card/90 hover:border-border"
          )}
        >
          <Cpu className="w-3.5 h-3.5 text-primary shrink-0" />
          <span className="font-medium truncate max-w-[130px] sm:max-w-[180px]">
            {selectedOption?.label || selectedModel || "Select model"}
          </span>
          <ChevronDown
            className={cn(
              "w-3.5 h-3.5 text-muted-foreground transition-transform duration-200 shrink-0",
              isOpen && "rotate-180"
            )}
          />
        </button>

        {/* Custom Obsidian Dropdown Menu */}
        {isOpen && (
          <div
            id="model-menu-listbox"
            role="listbox"
            aria-label="Available models"
            className="absolute left-0 top-full mt-1.5 z-50 min-w-[260px] max-h-72 overflow-y-auto rounded-xl border border-border/80 bg-card/95 backdrop-blur-xl p-1.5 shadow-2xl space-y-0.5 animate-in fade-in zoom-in-95 duration-150"
          >
            <div className="px-2 py-1 text-[10px] font-mono uppercase tracking-wider text-muted-foreground">
              Available Models ({options.length})
            </div>
            {options.map((option) => {
              const isSelected = option.id === selectedModel;
              return (
                <button
                  key={option.id}
                  role="option"
                  type="button"
                  aria-selected={isSelected}
                  onClick={() => handleSelect(option.id)}
                  className={cn(
                    "w-full flex items-center justify-between gap-2 px-2.5 py-1.5 rounded-lg text-xs transition-all text-left cursor-pointer",
                    isSelected
                      ? "bg-primary/15 text-primary font-medium border border-primary/30"
                      : "text-foreground hover:bg-muted/50 border border-transparent"
                  )}
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <Cpu
                      className={cn(
                        "w-3.5 h-3.5 shrink-0",
                        isSelected ? "text-primary" : "text-muted-foreground"
                      )}
                    />
                    <span className="truncate font-mono text-xs">{option.label}</span>
                  </div>
                  {isSelected && <Check className="w-3.5 h-3.5 text-primary shrink-0" />}
                </button>
              );
            })}
          </div>
        )}
      </div>

      <ToolsSwitch
        isSupported={supportsTools(selectedModel, modelCapabilities)}
        model={selectedModel}
      />
    </div>
  );
}

interface ToolsSwitchProps {
  isSupported: boolean;
  model: string;
}

function ToolsSwitch({ isSupported, model }: ToolsSwitchProps) {
  const toolsEnabled = useModelStore((s) => s.toolsEnabled);
  const setToolsEnabled = useModelStore((s) => s.setToolsEnabled);
  const isOn = toolsEnabled && isSupported;
  const label = isSupported ? "Toggle tools" : `${model} does not support tools`;

  return (
    <button
      type="button"
      role="switch"
      aria-checked={isOn}
      aria-disabled={!isSupported}
      disabled={!isSupported}
      title={label}
      aria-label={label}
      onClick={() => setToolsEnabled(!toolsEnabled)}
      className={cn(
        "flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-colors shadow-sm select-none",
        !isSupported &&
          "opacity-50 cursor-not-allowed border-border bg-card/20 text-muted-foreground",
        isOn && "border-emerald-500/30 bg-emerald-500/10 text-emerald-400",
        isSupported && !isOn && "border-border bg-card/40 text-muted-foreground hover:bg-muted"
      )}
    >
      <Wrench className="w-3.5 h-3.5" />
      <span className="hidden sm:inline">Tools</span>
      <div
        className={cn(
          "w-2 h-2 rounded-full transition-colors",
          !isSupported && "bg-muted-foreground/30",
          isOn && "bg-emerald-400",
          isSupported && !isOn && "bg-muted-foreground"
        )}
      />
    </button>
  );
}
