import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/features/auth/store/auth-store";
import { useConversationStore } from "@/features/conversations/store/conversation-store";
import { useModelStore } from "@/features/models/store/model-store";

/**
 * Restores the session on mount (redirecting to /login without one), loads the user's
 * conversations and models once signed in, and returns a sign-out that clears both.
 */
export function useWorkspaceSession() {
  const router = useRouter();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isLoading = useAuthStore((s) => s.isLoading);
  const checkAuth = useAuthStore((s) => s.checkAuth);
  const logout = useAuthStore((s) => s.logout);
  const loadConversations = useConversationStore((s) => s.loadConversations);
  const loadModels = useModelStore((s) => s.loadModels);

  useEffect(() => {
    void checkAuth().then((authenticated) => {
      if (!authenticated) {
        router.push("/login");
      }
    });
  }, [checkAuth, router]);

  useEffect(() => {
    if (isAuthenticated) {
      void loadConversations();
      void loadModels();
    }
  }, [isAuthenticated, loadConversations, loadModels]);

  const signOut = async () => {
    await logout();
    // The stores outlive client-side navigation; do not show this user's threads to the next one.
    useConversationStore.getState().reset();
    router.push("/login");
  };

  return { isAuthenticated, isLoading, signOut };
}
