"use client";

import * as React from "react";
import { Loader2, Lock, Sparkles, User as UserIcon, X } from "lucide-react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { getErrorMessage } from "@/lib/api-error";
import { useAuthStore } from "@/features/auth/store/auth-store";

export interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

/** Profile dialog: full name and the custom instructions injected into every prompt. */
export function SettingsModal({ isOpen, onClose }: SettingsModalProps) {
  const user = useAuthStore((s) => s.user);
  const updateProfile = useAuthStore((s) => s.updateProfile);

  const [fullName, setFullName] = React.useState("");
  const [customInstructions, setCustomInstructions] = React.useState("");
  const [isSaving, setIsSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  // Start from the saved profile each time the dialog opens.
  React.useEffect(() => {
    if (isOpen) {
      setFullName(user?.fullName || "");
      setCustomInstructions(user?.customInstructions || "");
      setError(null);
      setIsSaving(false);
    }
  }, [isOpen, user]);

  React.useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) {
    return null;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    setError(null);

    try {
      await updateProfile({
        fullName: fullName.trim(),
        customInstructions: customInstructions.trim(),
      });
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, "Failed to update profile. Please try again."));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="settings-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-xs animate-in fade-in duration-150"
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        className="relative w-full max-w-lg rounded-xl border border-border bg-card p-6 shadow-2xl text-card-foreground animate-in zoom-in-95 duration-150 sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between pb-4 border-b border-border">
          <div className="space-y-1">
            <h2
              id="settings-modal-title"
              className="text-lg font-semibold tracking-tight text-foreground flex items-center gap-2"
            >
              <UserIcon className="w-5 h-5 text-primary" />
              Settings & Profile
            </h2>
            <p className="text-xs text-muted-foreground">
              Customize your account profile and sovereign AI instructions.
            </p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={onClose}
            aria-label="Close settings"
            className="text-muted-foreground hover:text-foreground shrink-0 -mr-2"
          >
            <X className="w-4 h-4" />
          </Button>
        </div>

        <form onSubmit={handleSubmit} className="mt-5 space-y-4">
          {error && (
            <Alert size="sm" className="p-3">
              {error}
            </Alert>
          )}

          <div className="space-y-1.5">
            <label
              htmlFor="settings-email"
              className="text-xs font-medium text-foreground flex items-center justify-between"
            >
              <span>Email Address</span>
              <span className="text-[10px] text-muted-foreground font-normal flex items-center gap-1">
                <Lock className="w-3 h-3" /> Managed locally
              </span>
            </label>
            <div className="relative flex items-center">
              <Input
                id="settings-email"
                type="email"
                value={user?.email || ""}
                readOnly
                disabled
                className="bg-muted/40 text-muted-foreground border-border/80 cursor-not-allowed pl-8"
              />
              <Lock className="w-3.5 h-3.5 text-muted-foreground/70 absolute left-2.5 pointer-events-none" />
            </div>
          </div>

          <div className="space-y-1.5">
            <label htmlFor="settings-name" className="text-xs font-medium text-foreground">
              Full Name
            </label>
            <Input
              id="settings-name"
              type="text"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="e.g. Apurv Maurya"
              required
              className="bg-background/60"
            />
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label
                htmlFor="settings-custom-instructions"
                className="text-xs font-medium text-foreground flex items-center gap-1.5"
              >
                <Sparkles className="w-3.5 h-3.5 text-primary" />
                Custom Instructions
              </label>
              <span className="text-[10px] text-muted-foreground">Injected into system prompt</span>
            </div>
            <textarea
              id="settings-custom-instructions"
              rows={5}
              value={customInstructions}
              onChange={(e) => setCustomInstructions(e.target.value)}
              placeholder="e.g. Always respond in concise markdown bullet points. Prefer Python examples."
              className="w-full rounded-md border border-input bg-background/60 px-3 py-2 text-xs shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 resize-y"
            />
            <p className="text-[11px] text-muted-foreground">
              These instructions are automatically injected into the system prompt for every turn to
              steer AI responses.
            </p>
          </div>

          <div className="flex items-center justify-end gap-2 pt-3 border-t border-border">
            <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={isSaving}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={isSaving} className="min-w-[110px]">
              {isSaving ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin mr-1.5" />
                  Saving...
                </>
              ) : (
                "Save Changes"
              )}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
