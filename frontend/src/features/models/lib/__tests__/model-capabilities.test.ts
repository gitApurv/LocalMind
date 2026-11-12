import { describe, expect, it } from "vitest";
import { buildModelOptions, inferToolSupport, supportsTools } from "../model-capabilities";

describe("model capabilities", () => {
  it("infers tool support from the model family", () => {
    expect(inferToolSupport("gemma3:1b")).toBe(false);
    expect(inferToolSupport("nomic-embed-text")).toBe(false);
    expect(inferToolSupport("DeepSeek-R1:8b")).toBe(false);
    expect(inferToolSupport("qwen2.5:7b")).toBe(true);
  });

  it("prefers the reported capability over the inference", () => {
    expect(supportsTools("gemma3:12b", { "gemma3:12b": true })).toBe(true);
    expect(supportsTools("llama3.2", {})).toBe(true);
  });

  it("merges backend models with presets without duplicates", () => {
    const options = buildModelOptions(["mistral:7b", "gemma3:1b", "tinyllama:1b"]);

    expect(options.map((o) => o.id)).toEqual([
      "mistral:7b",
      "gemma3:1b",
      "tinyllama:1b",
      "qwen2.5:7b",
      "deepseek-r1:8b",
      "llama3.2",
    ]);
    expect(options[0].badge).toBe("Local");
    expect(options[1].badge).toBe("Local Lightweight");
    expect(options[2].badge).toBe("Lightweight");
  });
});
