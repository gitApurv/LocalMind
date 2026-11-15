import type { ModelInfo, ModelOption } from "../types";

export function supportsTools(model: string, capabilities: Record<string, boolean>): boolean {
  return Boolean(capabilities[model]);
}

/** Builds options strictly from models returned by the backend. */
export function buildModelOptions(models: ModelInfo[]): ModelOption[] {
  return models.map((m) => ({
    id: m.name,
    label: m.name,
  }));
}
