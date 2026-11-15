import { describe, expect, it } from "vitest";
import { buildModelOptions, supportsTools } from "../model-capabilities";

describe("model capabilities", () => {
  it("determines tool support strictly from the backend capabilities map", () => {
    const capabilities = {
      "qwen2.5:7b": true,
      "gemma3:1b": false,
      "custom-model": true,
    };

    expect(supportsTools("qwen2.5:7b", capabilities)).toBe(true);
    expect(supportsTools("gemma3:1b", capabilities)).toBe(false);
    expect(supportsTools("custom-model", capabilities)).toBe(true);
    expect(supportsTools("unknown-model", capabilities)).toBe(false);
  });

  it("builds options strictly from models returned by the backend", () => {
    const options = buildModelOptions([
      { name: "mistral:7b", supportsTools: false },
      { name: "gemma3:1b", supportsTools: false },
      { name: "llama3.2:latest", supportsTools: false },
      { name: "qwen2.5:7b", supportsTools: true },
    ]);

    expect(options).toEqual([
      { id: "mistral:7b", label: "mistral:7b" },
      { id: "gemma3:1b", label: "gemma3:1b" },
      { id: "llama3.2:latest", label: "llama3.2:latest" },
      { id: "qwen2.5:7b", label: "qwen2.5:7b" },
    ]);
  });
});
