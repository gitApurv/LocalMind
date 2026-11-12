import { describe, it, expect, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ModelSelector } from "../model-selector";
import { useModelStore } from "../../store/model-store";

describe("ModelSelector", () => {
  beforeEach(() => {
    useModelStore.setState({
      selectedModel: "qwen2.5:7b",
      availableModels: ["gemma3:1b", "qwen2.5:7b", "deepseek-r1:8b"],
      modelCapabilities: { "gemma3:1b": false, "qwen2.5:7b": true, "deepseek-r1:8b": false },
      toolsEnabled: true,
    });
  });

  it("renders default model and allows switching", () => {
    useModelStore.setState({ selectedModel: "gemma3:1b" });
    render(<ModelSelector />);

    // Shows currently selected model text in options or badge
    expect(screen.getAllByText(/Local Lightweight/i).length).toBeGreaterThan(0);

    // Select dropdown trigger
    const select = screen.getByRole("combobox", { name: /select model/i }) as HTMLSelectElement;
    expect(select).toBeDefined();
    expect(select.value).toBe("gemma3:1b");

    // Change model
    fireEvent.change(select, { target: { value: "qwen2.5:7b" } });
    expect(useModelStore.getState().selectedModel).toBe("qwen2.5:7b");
  });

  it("toggles tools enabled state for tool-capable model", () => {
    render(<ModelSelector />);

    const toolsSwitch = screen.getByRole("switch");
    expect(toolsSwitch).toBeDefined();
    expect(toolsSwitch.getAttribute("aria-checked")).toBe("true");
    expect(toolsSwitch.hasAttribute("disabled")).toBe(false);

    fireEvent.click(toolsSwitch);
    expect(useModelStore.getState().toolsEnabled).toBe(false);
    expect(toolsSwitch.getAttribute("aria-checked")).toBe("false");
  });

  it("disables Tools button with explanatory title when model does not support tools", () => {
    useModelStore.setState({
      selectedModel: "gemma3:1b",
      toolsEnabled: false,
    });
    render(<ModelSelector />);

    const toolsSwitch = screen.getByRole("switch") as HTMLButtonElement;
    expect(toolsSwitch.disabled).toBe(true);
    expect(toolsSwitch.getAttribute("title")).toContain("gemma3:1b does not support tools");

    fireEvent.click(toolsSwitch);
    expect(useModelStore.getState().toolsEnabled).toBe(false);
  });
});
