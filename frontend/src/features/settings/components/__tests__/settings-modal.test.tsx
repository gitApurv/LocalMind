import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import * as React from "react";
import { SettingsModal } from "../settings-modal";
import { useAuthStore } from "@/features/auth/store/auth-store";

describe("SettingsModal", () => {
  const mockOnClose = vi.fn();

  beforeEach(() => {
    vi.restoreAllMocks();
    mockOnClose.mockClear();

    useAuthStore.setState({
      user: {
        id: "u-1",
        email: "alice@localmind.io",
        fullName: "Alice Sovereign",
        customInstructions: "Answer concisely in markdown.",
      },
      isAuthenticated: true,
      isLoading: false,
      error: null,
    });
  });

  it("does not render when isOpen is false", () => {
    render(<SettingsModal isOpen={false} onClose={mockOnClose} />);
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("initial population of existing fullName, email, and customInstructions", () => {
    render(<SettingsModal isOpen={true} onClose={mockOnClose} />);

    expect(screen.getByRole("dialog")).toBeDefined();

    // Email is read-only
    const emailInput = screen.getByDisplayValue("alice@localmind.io");
    expect(emailInput).toBeDefined();
    expect((emailInput as HTMLInputElement).readOnly).toBe(true);

    // Full name is populated
    const nameInput = screen.getByDisplayValue("Alice Sovereign");
    expect(nameInput).toBeDefined();

    // Custom instructions are populated
    const instructionsInput = screen.getByDisplayValue("Answer concisely in markdown.");
    expect(instructionsInput).toBeDefined();
  });

  it("submitting the form calls updateProfile and closes the modal", async () => {
    const updateProfileMock = vi.fn().mockResolvedValue(undefined);
    useAuthStore.setState({ updateProfile: updateProfileMock });

    render(<SettingsModal isOpen={true} onClose={mockOnClose} />);

    const nameInput = screen.getByDisplayValue("Alice Sovereign");
    fireEvent.change(nameInput, { target: { value: "Alice Wonderland" } });

    const instructionsInput = screen.getByDisplayValue("Answer concisely in markdown.");
    fireEvent.change(instructionsInput, {
      target: { value: "Prefer Python and bullet points." },
    });

    const saveButton = screen.getByRole("button", { name: /save changes/i });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(updateProfileMock).toHaveBeenCalledWith({
        fullName: "Alice Wonderland",
        customInstructions: "Prefer Python and bullet points.",
      });
      expect(mockOnClose).toHaveBeenCalledTimes(1);
    });
  });

  it("error messages are displayed on API failure", async () => {
    const updateProfileMock = vi.fn().mockRejectedValue(new Error("Unable to save instructions."));
    useAuthStore.setState({ updateProfile: updateProfileMock });

    render(<SettingsModal isOpen={true} onClose={mockOnClose} />);

    const saveButton = screen.getByRole("button", { name: /save changes/i });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(screen.getByText(/unable to save instructions/i)).toBeDefined();
    });

    expect(mockOnClose).not.toHaveBeenCalled();
  });

  it("calls onClose when Cancel button or close icon is clicked", () => {
    render(<SettingsModal isOpen={true} onClose={mockOnClose} />);

    const cancelButton = screen.getByRole("button", { name: /cancel/i });
    fireEvent.click(cancelButton);
    expect(mockOnClose).toHaveBeenCalledTimes(1);

    mockOnClose.mockClear();

    const closeIconButton = screen.getByLabelText(/close settings/i);
    fireEvent.click(closeIconButton);
    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });
});
