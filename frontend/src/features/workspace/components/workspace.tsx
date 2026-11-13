"use client";

import * as React from "react";
import { ChatContainer } from "@/features/chat/components/chat-container";
import { SettingsModal } from "@/features/settings/components/settings-modal";
import { useWorkspaceSession } from "../hooks/use-workspace-session";
import { LoadingScreen } from "./loading-screen";
import { WorkspaceSidebar } from "./workspace-sidebar";

/** Authenticated app shell: sidebar, chat and settings dialog. */
export function Workspace() {
  const { isAuthenticated, isLoading, signOut } = useWorkspaceSession();
  const [isSidebarOpen, setIsSidebarOpen] = React.useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = React.useState(false);
  const closeSettings = React.useCallback(() => setIsSettingsOpen(false), []);

  if (isLoading) {
    return <LoadingScreen />;
  }
  if (!isAuthenticated) {
    return null;
  }

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-background relative">
      {isSidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/60 md:hidden backdrop-blur-xs"
          onClick={() => setIsSidebarOpen(false)}
        />
      )}

      <WorkspaceSidebar
        isOpen={isSidebarOpen}
        onClose={() => setIsSidebarOpen(false)}
        onOpenSettings={() => setIsSettingsOpen(true)}
        onSignOut={() => void signOut()}
      />

      <main className="flex-1 flex flex-col h-full overflow-hidden">
        <ChatContainer onToggleSidebar={() => setIsSidebarOpen((open) => !open)} />
      </main>

      <SettingsModal isOpen={isSettingsOpen} onClose={closeSettings} />
    </div>
  );
}
