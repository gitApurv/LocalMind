import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WorkspaceSidebar } from "../workspace-sidebar";
import { useModelStore } from "@/features/models/store/model-store";

describe("WorkspaceSidebar", () => {
  beforeEach(() => {
    useModelStore.setState({
      availableModels: ["gemma3:1b", "qwen2.5:7b"],
      selectedModel: "gemma3:1b",
    });
  });

  it("renders air-gapped node security badge", () => {
    render(
      <WorkspaceSidebar
        isOpen={true}
        onClose={vi.fn()}
        onOpenSettings={vi.fn()}
        onSignOut={vi.fn()}
      />
    );
    expect(screen.getByText("Air-Gapped Node")).toBeDefined();
  });

  it("does not render local inference node telemetry card", () => {
    render(
      <WorkspaceSidebar
        isOpen={true}
        onClose={vi.fn()}
        onOpenSettings={vi.fn()}
        onSignOut={vi.fn()}
      />
    );
    expect(screen.queryByText("Local Inference Node")).toBeNull();
    expect(screen.queryByText("Ollama Online")).toBeNull();
  });

  it("hides sidebar completely when isOpen is false", () => {
    const { container } = render(
      <WorkspaceSidebar
        isOpen={false}
        onClose={vi.fn()}
        onOpenSettings={vi.fn()}
        onSignOut={vi.fn()}
      />
    );
    const aside = container.querySelector("aside");
    expect(aside?.className).toContain("hidden");
  });
});
