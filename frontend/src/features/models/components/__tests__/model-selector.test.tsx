import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ModelSelector } from "../model-selector";
import { useModelStore } from "../../store/model-store";
import { useConversationStore } from "@/features/conversations/store/conversation-store";

describe("ModelSelector", () => {
  beforeEach(() => {
    useModelStore.setState({
      models: [
        { name: "gemma3:1b", supportsTools: false },
        { name: "qwen2.5:7b", supportsTools: true },
        { name: "deepseek-r1:8b", supportsTools: false },
      ],
      selectedModel: "qwen2.5:7b",
      availableModels: ["gemma3:1b", "qwen2.5:7b", "deepseek-r1:8b"],
      modelCapabilities: { "gemma3:1b": false, "qwen2.5:7b": true, "deepseek-r1:8b": false },
      toolsEnabled: true,
    });
  });

  it("renders default model and allows switching via custom dropdown", () => {
    useModelStore.setState({ selectedModel: "gemma3:1b" });
    render(<ModelSelector />);

    // Dropdown combobox trigger button
    const trigger = screen.getByRole("combobox", { name: /select model/i });
    expect(trigger).toBeDefined();
    expect(screen.getByText("gemma3:1b")).toBeDefined();

    // Open custom dropdown
    fireEvent.click(trigger);
    expect(screen.getByRole("listbox", { name: /available models/i })).toBeDefined();

    // Select qwen2.5:7b option
    const option = screen.getByRole("option", { name: /qwen2.5:7b/i });
    fireEvent.click(option);
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

  it("persists model change to active conversation if present", () => {
    useConversationStore.setState({ activeConversationId: "c-active" });
    const updateSpy = vi
      .spyOn(useConversationStore.getState(), "updateConversationModel")
      .mockResolvedValueOnce();

    render(<ModelSelector />);
    const trigger = screen.getByRole("combobox", { name: /select model/i });
    fireEvent.click(trigger);

    const option = screen.getByRole("option", { name: /gemma3:1b/i });
    fireEvent.click(option);

    expect(updateSpy).toHaveBeenCalledWith("c-active", "gemma3:1b");
  });

  it("only displays models available on the device, omitting uninstalled presets", () => {
    useModelStore.setState({
      models: [{ name: "mistral:7b", supportsTools: false }],
      availableModels: ["mistral:7b"],
      selectedModel: "mistral:7b",
    });

    render(<ModelSelector />);
    const trigger = screen.getByRole("combobox", { name: /select model/i });
    fireEvent.click(trigger);

    expect(screen.getByRole("option", { name: /mistral:7b/i })).toBeDefined();
    expect(screen.queryByRole("option", { name: /llama3.2/i })).toBeNull();
    expect(screen.queryByRole("option", { name: /deepseek-r1/i })).toBeNull();
    expect(screen.queryByRole("option", { name: /qwen2.5/i })).toBeNull();
  });
});
