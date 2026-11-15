import { create } from "zustand";
import { modelsApi } from "../api/models-api";
import { supportsTools } from "../lib/model-capabilities";
import type { ModelInfo } from "../types";

export interface ModelState {
  models: ModelInfo[];
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
  models: [],
  availableModels: [],
  selectedModel: "",
  modelCapabilities: {},
  toolsEnabled: false,

  loadModels: async () => {
    try {
      const response = await modelsApi.list();
      const models = response?.models ?? [];
      if (models.length === 0) return;

      const names = models.map((m) => m.name);
      const capabilities = Object.fromEntries(
        models.map((m) => [m.name, Boolean(m.supportsTools)])
      );
      const defaultCandidate =
        response?.defaultModel && names.includes(response.defaultModel)
          ? response.defaultModel
          : names[0];
      const current = get().selectedModel;
      const selectedModel = current && names.includes(current) ? current : defaultCandidate;
      const isToolSupported = supportsTools(selectedModel, capabilities);

      set({
        models,
        availableModels: names,
        modelCapabilities: capabilities,
        selectedModel,
        toolsEnabled: isToolSupported,
      });
    } catch (error) {
      console.warn("Failed to query Ollama models:", error);
    }
  },

  setSelectedModel: (model) =>
    set((state) => ({
      selectedModel: model,
      toolsEnabled: supportsTools(model, state.modelCapabilities),
    })),

  setToolsEnabled: (enabled) => set({ toolsEnabled: enabled }),
}));
