import type { ModelOption } from "./types";

export const DEFAULT_MODEL = "gemma3:1b";

/** Models always offered in the dropdown, even before the backend catalogue has loaded. */
export const MODEL_PRESETS: ModelOption[] = [
  { id: "gemma3:1b", label: "gemma3:1b", badge: "Local Lightweight" },
  { id: "qwen2.5:7b", label: "qwen2.5:7b", badge: "Agentic" },
  { id: "deepseek-r1:8b", label: "deepseek-r1:8b", badge: "Reasoning" },
  { id: "llama3.2", label: "llama3.2", badge: "General" },
];

/** Tool support assumed until `/api/models` reports the real capabilities. */
export const DEFAULT_MODEL_CAPABILITIES: Record<string, boolean> = {
  "gemma3:1b": false,
  "deepseek-r1:8b": false,
  "qwen2.5:7b": true,
};
