import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ContextInspector } from "../context-inspector";

describe("ContextInspector", () => {
  it("renders tabs and triggers tab change", () => {
    const onTabChange = vi.fn();
    render(
      <ContextInspector
        isOpen={true}
        activeTab="docs"
        onTabChange={onTabChange}
        onClose={() => {}}
        conversationId="c-1"
      />
    );

    expect(screen.getByRole("tab", { name: /docs/i })).toBeDefined();
    const memoryTab = screen.getByRole("tab", { name: /memory/i });
    fireEvent.click(memoryTab);
    expect(onTabChange).toHaveBeenCalledWith("memory");
  });

  it("does not render when isOpen is false", () => {
    const { container } = render(
      <ContextInspector
        isOpen={false}
        activeTab="docs"
        onTabChange={() => {}}
        onClose={() => {}}
        conversationId="c-1"
      />
    );
    expect(container.firstChild).toBeNull();
  });
});
