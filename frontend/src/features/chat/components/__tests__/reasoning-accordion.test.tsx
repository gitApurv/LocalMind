import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ReasoningAccordion } from "../reasoning-accordion";

describe("ReasoningAccordion", () => {
  it("renders reasoning text and duration indicator", () => {
    render(
      <ReasoningAccordion
        content="Analyzing the user question and checking memory context."
        durationSec={2.4}
        isStreaming={false}
      />
    );

    // Summary contains thinking duration
    expect(screen.getByText(/thought for 2\.4s/i)).toBeDefined();

    // Content is rendered
    expect(
      screen.getByText(/analyzing the user question and checking memory context/i)
    ).toBeDefined();
  });

  it("shows active thinking indicator when isStreaming is true", () => {
    render(<ReasoningAccordion content="Generating thoughts..." isStreaming={true} />);

    expect(screen.getByText(/thinking\.\.\./i)).toBeDefined();
  });

  it("allows toggling open/close state", () => {
    const { container } = render(
      <ReasoningAccordion
        content="Deep reasoning steps here."
        durationSec={1.5}
        isStreaming={false}
      />
    );

    const details = container.querySelector("details");
    expect(details).toBeDefined();

    const summary = container.querySelector("summary");
    expect(summary).toBeDefined();

    // Click summary
    fireEvent.click(summary!);
    expect(details).toBeDefined();
  });
});
