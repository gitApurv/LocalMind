import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/lib/api-client";
import { resetWorkspaceStores } from "@/test/factories";
import { useModelStore } from "../model-store";

describe("useModelStore", () => {
  beforeEach(() => {
    resetWorkspaceStores();
    vi.restoreAllMocks();
  });

  it("loadModels() stores the catalogue and capabilities from the backend", async () => {
    vi.spyOn(apiClient, "get").mockResolvedValueOnce({
      models: [
        { name: "gemma3:1b", supportsTools: false },
        { name: "deepseek-r1:8b", supportsTools: false },
        { name: "qwen2.5:7b", supportsTools: true },
      ],
    });

    await useModelStore.getState().loadModels();

    const state = useModelStore.getState();
    expect(apiClient.get).toHaveBeenCalledWith("/api/models");
    expect(state.availableModels).toEqual(["gemma3:1b", "deepseek-r1:8b", "qwen2.5:7b"]);
    expect(state.modelCapabilities).toEqual({
      "gemma3:1b": false,
      "deepseek-r1:8b": false,
      "qwen2.5:7b": true,
    });
  });

  it("loadModels() falls back to the first model when the selected one is missing", async () => {
    useModelStore.setState({ selectedModel: "llama3.2", toolsEnabled: true });
    vi.spyOn(apiClient, "get").mockResolvedValueOnce({
      models: [{ name: "gemma3:1b", supportsTools: false }],
    });

    await useModelStore.getState().loadModels();

    expect(useModelStore.getState().selectedModel).toBe("gemma3:1b");
    expect(useModelStore.getState().toolsEnabled).toBe(false);
  });

  it("loadModels() keeps the defaults when the backend is unreachable", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    vi.spyOn(apiClient, "get").mockRejectedValueOnce({ status: 0, message: "offline" });

    await useModelStore.getState().loadModels();

    expect(useModelStore.getState().availableModels).toEqual(["gemma3:1b"]);
  });

  it("setSelectedModel() switches tools off for models that cannot call them", () => {
    useModelStore.setState({ toolsEnabled: true });

    useModelStore.getState().setSelectedModel("gemma3:1b");
    expect(useModelStore.getState().toolsEnabled).toBe(false);

    useModelStore.getState().setToolsEnabled(true);
    useModelStore.getState().setSelectedModel("qwen2.5:7b");
    expect(useModelStore.getState().toolsEnabled).toBe(true);
  });
});
