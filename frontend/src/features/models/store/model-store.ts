import { create } from "zustand";
import { modelsApi } from "../api/models-api";
import { DEFAULT_MODEL, DEFAULT_MODEL_CAPABILITIES } from "../constants";
import { inferToolSupport, supportsTools } from "../lib/model-capabilities";

export interface ModelState {
  availableModels: string[];
  selectedModel: string;
  /** Tool support per model name. */
  modelCapabilities: Record<string, boolean>;
  toolsEnabled: boolean;

  loadModels: () => Promise<void>;
  /** Selects a model and switches tools off when it cannot call them. */
  setSelectedModel: (model: string) => void;
  setToolsEnabled: (enabled: boolean) => void;
}

export const useModelStore = create<ModelState>((set, get) => ({
  availableModels: [DEFAULT_MODEL],
  selectedModel: DEFAULT_MODEL,
  modelCapabilities: DEFAULT_MODEL_CAPABILITIES,
  toolsEnabled: false,

  loadModels: async () => {
    try {
      const response = await modelsApi.list();
      const models = response?.models ?? [];
      if (models.length === 0) return;

      const names = models.map((m) => m.name);
      const capabilities = Object.fromEntries(
        models.map((m) => [m.name, m.supportsTools ?? inferToolSupport(m.name)])
      );
      const current = get().selectedModel;
      const selectedModel = names.includes(current) ? current : names[0];

      set((state) => ({
        availableModels: names,
        modelCapabilities: capabilities,
        selectedModel,
        toolsEnabled: state.toolsEnabled && supportsTools(selectedModel, capabilities),
      }));
    } catch (error) {
      console.warn("Failed to query Ollama models, keeping defaults:", error);
    }
  },

  setSelectedModel: (model) =>
    set((state) => ({
      selectedModel: model,
      toolsEnabled: state.toolsEnabled && supportsTools(model, state.modelCapabilities),
    })),

  setToolsEnabled: (enabled) => set({ toolsEnabled: enabled }),
}));
