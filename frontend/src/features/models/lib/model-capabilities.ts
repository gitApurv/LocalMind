import { MODEL_PRESETS } from "../constants";
import type { ModelOption } from "../types";

// Model families that Ollama serves without function-calling support.
const FAMILIES_WITHOUT_TOOLS = ["gemma", "embed", "deepseek-r1"];

/** Guesses tool support from the model name, for models the backend has not described. */
export function inferToolSupport(model: string): boolean {
  const name = model.toLowerCase();
  return !FAMILIES_WITHOUT_TOOLS.some((family) => name.includes(family));
}

export function supportsTools(model: string, capabilities: Record<string, boolean>): boolean {
  return capabilities[model] ?? inferToolSupport(model);
}

/** Merges the backend catalogue with the presets, without duplicates, preserving order. */
export function buildModelOptions(availableModels: string[]): ModelOption[] {
  const ids = new Set([...availableModels, ...MODEL_PRESETS.map((preset) => preset.id)]);
  return Array.from(ids, (id) => {
    const preset = MODEL_PRESETS.find((p) => p.id === id);
    return preset ?? { id, label: id, badge: id.includes("1b") ? "Lightweight" : "Local" };
  });
}

export function findPreset(model: string): ModelOption | undefined {
  return MODEL_PRESETS.find((preset) => preset.id === model);
}
