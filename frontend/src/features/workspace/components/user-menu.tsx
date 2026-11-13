import { LogOut, Settings } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/features/auth/store/auth-store";

export interface UserMenuProps {
  onOpenSettings: () => void;
  onSignOut: () => void;
}

/** Sidebar footer with the signed-in user, settings and sign-out. */
export function UserMenu({ onOpenSettings, onSignOut }: UserMenuProps) {
  const user = useAuthStore((s) => s.user);

  return (
    <div className="p-3 border-t border-border flex items-center justify-between bg-card/80 gap-2">
      <button
        type="button"
        onClick={onOpenSettings}
        title="Open user settings"
        aria-label="Open user settings"
        className="flex items-center gap-2 truncate text-left rounded-lg p-1 -m-1 hover:bg-muted/50 transition-colors cursor-pointer group flex-1 min-w-0"
      >
        <div className="w-7 h-7 rounded-full bg-secondary flex items-center justify-center text-xs font-semibold uppercase text-secondary-foreground shrink-0 group-hover:ring-1 group-hover:ring-primary/40 transition-all">
          {user?.fullName?.charAt(0) || "U"}
        </div>
        <div className="truncate text-xs">
          <p className="font-medium truncate text-foreground group-hover:text-primary transition-colors">
            {user?.fullName || "User"}
          </p>
          <p className="text-muted-foreground truncate text-[11px]">{user?.email}</p>
        </div>
      </button>
      <div className="flex items-center gap-0.5 shrink-0">
        <Button
          variant="ghost"
          size="icon"
          onClick={onOpenSettings}
          title="Settings & Profile"
          aria-label="Settings & Profile"
          className="text-muted-foreground hover:text-foreground shrink-0 w-8 h-8"
        >
          <Settings className="w-4 h-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          onClick={onSignOut}
          title="Sign out"
          aria-label="Sign out"
          className="text-muted-foreground hover:text-destructive shrink-0 w-8 h-8"
        >
          <LogOut className="w-4 h-4" />
        </Button>
      </div>
    </div>
  );
}
