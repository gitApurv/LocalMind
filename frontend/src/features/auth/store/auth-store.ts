import { create } from "zustand";
import { apiClient } from "@/lib/api-client";
import { SESSION_EXPIRED_MESSAGE, getErrorMessage } from "@/lib/api-error";
import { authApi } from "../api/auth-api";
import { parseUserFromToken } from "../lib/parse-user-from-token";
import type { AuthResponse, UpdateProfileRequest, User } from "../types";

export interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;

  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, fullName: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Restores the session from the refresh cookie; resolves to whether the user is signed in. */
  checkAuth: () => Promise<boolean>;
  clearError: () => void;
  fetchProfile: () => Promise<void>;
  updateProfile: (request: UpdateProfileRequest) => Promise<void>;
}

const signedOut = {
  user: null,
  isAuthenticated: false,
  isLoading: false,
} as const;

export const useAuthStore = create<AuthState>((set, get) => {
  apiClient.onAuthFailure(() => set({ ...signedOut, error: SESSION_EXPIRED_MESSAGE }));

  const startSession = async (response: AuthResponse) => {
    apiClient.setAccessToken(response.accessToken);
    set({
      user: response.user ?? parseUserFromToken(response.accessToken),
      isAuthenticated: true,
      isLoading: false,
      error: null,
    });
    try {
      await get().fetchProfile();
    } catch {
      // The profile only adds custom instructions; the session works without it.
    }
  };

  const endSession = () => {
    apiClient.setAccessToken(null);
    set({ ...signedOut, error: null });
  };

  const mergeUser = (user: User) =>
    set((state) => ({ user: state.user ? { ...state.user, ...user } : user }));

  return {
    user: null,
    isAuthenticated: false,
    isLoading: true,
    error: null,

    clearError: () => set({ error: null }),

    login: async (email, password) => {
      set({ isLoading: true, error: null });
      let response: AuthResponse;
      try {
        response = await authApi.login({ email, password });
      } catch (error) {
        set({
          isLoading: false,
          error: getErrorMessage(error, "Failed to log in. Please check your credentials."),
        });
        throw error;
      }
      await startSession(response);
    },

    register: async (email, password, fullName) => {
      set({ isLoading: true, error: null });
      try {
        await authApi.register({ email, password, fullName });
      } catch (error) {
        set({
          isLoading: false,
          error: getErrorMessage(error, "Failed to create account. Please try again."),
        });
        throw error;
      }
      await get().login(email, password);
    },

    logout: async () => {
      set({ isLoading: true });
      try {
        await authApi.logout();
      } catch (error) {
        console.warn("Logout request failed:", error);
      }
      endSession();
    },

    checkAuth: async () => {
      set({ isLoading: true });
      let response: AuthResponse;
      try {
        response = await authApi.refresh();
      } catch {
        endSession();
        return false;
      }
      await startSession(response);
      return true;
    },

    fetchProfile: async () => {
      mergeUser(await authApi.getProfile());
    },

    updateProfile: async (request) => {
      try {
        mergeUser(await authApi.updateProfile(request));
        set({ error: null });
      } catch (error) {
        set({ error: getErrorMessage(error, "Failed to update profile.") });
        throw error;
      }
    },
  };
});
