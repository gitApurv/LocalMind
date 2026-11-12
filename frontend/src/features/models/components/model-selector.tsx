"use client";

import * as React from "react";
import { Cpu, Wrench } from "lucide-react";
import { cn } from "@/lib/utils";
import { buildModelOptions, findPreset, supportsTools } from "../lib/model-capabilities";
import { useModelStore } from "../store/model-store";

export function ModelSelector() {
  const selectedModel = useModelStore((s) => s.selectedModel);
  const availableModels = useModelStore((s) => s.availableModels);
  const modelCapabilities = useModelStore((s) => s.modelCapabilities);
  const setSelectedModel = useModelStore((s) => s.setSelectedModel);

  const options = React.useMemo(() => buildModelOptions(availableModels), [availableModels]);
  const badge = findPreset(selectedModel)?.badge;

  return (
    <div className="flex items-center gap-2">
      <div className="relative flex items-center">
        <label htmlFor="model-select" className="sr-only">
          Select model
        </label>
        <div className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-border bg-card/60 text-xs text-foreground focus-within:ring-1 focus-within:ring-primary shadow-sm">
          <Cpu className="w-3.5 h-3.5 text-primary shrink-0" />
          <select
            id="model-select"
            aria-label="Select model"
            value={selectedModel}
            onChange={(e) => setSelectedModel(e.target.value)}
            className="bg-transparent text-xs font-medium text-foreground focus:outline-none cursor-pointer pr-1"
          >
            {options.map((option) => (
              <option key={option.id} value={option.id} className="bg-card text-foreground">
                {option.label} ({option.badge})
              </option>
            ))}
          </select>
          {badge && (
            <span className="hidden sm:inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold bg-primary/10 text-primary border border-primary/20">
              {badge}
            </span>
          )}
        </div>
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
