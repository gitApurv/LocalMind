import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ToolStatusBadge } from "../tool-status-badge";

describe("ToolStatusBadge", () => {
  it("renders executing status with animated indicator for known tool", () => {
    render(<ToolStatusBadge tool="searchWeb" status="executing" />);

    expect(screen.getByRole("status")).toBeDefined();
    expect(screen.getByText(/running web search/i)).toBeDefined();
  });

  it("renders completed status with checkmark for calculateExpression", () => {
    render(<ToolStatusBadge tool="calculateExpression" status="completed" />);

    expect(screen.getByRole("status")).toBeDefined();
    expect(screen.getByText(/used math calculator/i)).toBeDefined();
  });

  it("renders document search tool correctly", () => {
    render(<ToolStatusBadge tool="searchUploadedDocs" status="completed" />);

    expect(screen.getByText(/used document knowledge/i)).toBeDefined();
  });

  it("handles unknown tool gracefully", () => {
    render(<ToolStatusBadge tool="custom_tool_xyz" status="executing" />);

    expect(screen.getByText(/running custom_tool_xyz/i)).toBeDefined();
  });
});
