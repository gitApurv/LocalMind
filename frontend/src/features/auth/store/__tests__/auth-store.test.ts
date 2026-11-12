import { describe, it, expect, beforeEach, vi } from "vitest";
import { useAuthStore } from "../auth-store";
import { apiClient } from "@/lib/api-client";

describe("useAuthStore", () => {
  beforeEach(() => {
    useAuthStore.setState({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,
    });
    vi.restoreAllMocks();
  });

  it("login() should update state upon successful response", async () => {
    const mockUser = {
      id: "u-1",
      email: "test@example.com",
      fullName: "Test User",
    };

    vi.spyOn(apiClient, "post").mockResolvedValueOnce({
      accessToken: "token-xyz",
      tokenType: "Bearer",
      expiresIn: 900,
      user: mockUser,
    });

    await useAuthStore.getState().login("test@example.com", "password123");

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(true);
    expect(state.user).toEqual(mockUser);
    expect(state.error).toBeNull();
    expect(apiClient.getAccessToken()).toBe("token-xyz");
  });

  it("login() should record error when authentication fails", async () => {
    vi.spyOn(apiClient, "post").mockRejectedValueOnce({
      status: 401,
      message: "Bad credentials",
    });

    await expect(useAuthStore.getState().login("test@example.com", "wrong")).rejects.toBeDefined();

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.user).toBeNull();
    expect(state.error).toBe("Bad credentials");
  });

  it("register() should call register and then login automatically", async () => {
    const postSpy = vi.spyOn(apiClient, "post");

    // 1st call: register
    postSpy.mockResolvedValueOnce({});
    // 2nd call: login
    postSpy.mockResolvedValueOnce({
      accessToken: "token-registered",
      tokenType: "Bearer",
      expiresIn: 900,
      user: { id: "u-2", email: "new@example.com", fullName: "New User" },
    });

    await useAuthStore.getState().register("new@example.com", "password123", "New User");

    expect(postSpy).toHaveBeenCalledTimes(2);
    expect(postSpy).toHaveBeenNthCalledWith(1, "/api/auth/register", {
      email: "new@example.com",
      password: "password123",
      fullName: "New User",
    });

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(true);
    expect(state.user?.email).toBe("new@example.com");
  });

  it("logout() should clear authentication state and tokens", async () => {
    useAuthStore.setState({
      user: { id: "u-1", email: "test@example.com", fullName: "Test User" },
      isAuthenticated: true,
    });
    apiClient.setAccessToken("some-token");

    vi.spyOn(apiClient, "post").mockResolvedValueOnce({});

    await useAuthStore.getState().logout();

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.user).toBeNull();
    expect(apiClient.getAccessToken()).toBeNull();
  });

  it("checkAuth() should return true and set user when refresh succeeds", async () => {
    const mockUser = {
      id: "u-3",
      email: "refreshed@example.com",
      fullName: "Refreshed User",
    };

    vi.spyOn(apiClient, "post").mockResolvedValueOnce({
      accessToken: "token-refreshed",
      user: mockUser,
    });

    const result = await useAuthStore.getState().checkAuth();

    expect(result).toBe(true);
    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(true);
    expect(state.user).toEqual(mockUser);
    expect(apiClient.getAccessToken()).toBe("token-refreshed");
  });

  it("checkAuth() should extract user from JWT claims when response.user is absent", async () => {
    const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }));
    const payload = btoa(
      JSON.stringify({
        sub: "u-jwt",
        userId: "u-jwt",
        email: "jwt@example.com",
        fullName: "JWT User",
      })
    );
    const mockJwt = `${header}.${payload}.signature`;

    vi.spyOn(apiClient, "post").mockResolvedValueOnce({
      accessToken: mockJwt,
      tokenType: "Bearer",
      expiresIn: 900,
    });

    const result = await useAuthStore.getState().checkAuth();

    expect(result).toBe(true);
    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(true);
    expect(state.user).toEqual({
      id: "u-jwt",
      email: "jwt@example.com",
      fullName: "JWT User",
    });
    expect(apiClient.getAccessToken()).toBe(mockJwt);
  });

  it("checkAuth() should return false when refresh fails", async () => {
    vi.spyOn(apiClient, "post").mockRejectedValueOnce(new Error("No cookie"));

    const result = await useAuthStore.getState().checkAuth();

    expect(result).toBe(false);
    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.user).toBeNull();
  });

  it("fetchProfile() should retrieve profile from /api/auth/me and update user in state", async () => {
    const profile = {
      id: "u-1",
      email: "test@example.com",
      fullName: "Test User",
      customInstructions: "Prefer concise answers.",
    };

    vi.spyOn(apiClient, "get").mockResolvedValueOnce(profile);

    await useAuthStore.getState().fetchProfile();

    const state = useAuthStore.getState();
    expect(apiClient.get).toHaveBeenCalledWith("/api/auth/me");
    expect(state.user).toEqual(profile);
  });

  it("updateProfile() should call PATCH /api/auth/me and update user state", async () => {
    useAuthStore.setState({
      user: { id: "u-1", email: "test@example.com", fullName: "Original Name" },
      isAuthenticated: true,
    });

    const updated = {
      id: "u-1",
      email: "test@example.com",
      fullName: "Updated Name",
      customInstructions: "Be friendly and write TypeScript.",
    };

    const patchSpy = vi.spyOn(apiClient, "patch").mockResolvedValueOnce(updated);

    await useAuthStore.getState().updateProfile({
      fullName: "Updated Name",
      customInstructions: "Be friendly and write TypeScript.",
    });

    expect(patchSpy).toHaveBeenCalledWith("/api/auth/me", {
      fullName: "Updated Name",
      customInstructions: "Be friendly and write TypeScript.",
    });

    const state = useAuthStore.getState();
    expect(state.user).toEqual(updated);
  });

  it("updateProfile() should set error and throw when API call fails", async () => {
    vi.spyOn(apiClient, "patch").mockRejectedValueOnce({
      status: 400,
      message: "Validation failed",
    });

    await expect(
      useAuthStore.getState().updateProfile({
        fullName: "",
      })
    ).rejects.toBeDefined();

    const state = useAuthStore.getState();
    expect(state.error).toBe("Validation failed");
  });

  it("checkAuth() should hydrate customInstructions via fetchProfile", async () => {
    vi.spyOn(apiClient, "post").mockResolvedValueOnce({
      accessToken: "token-check",
      tokenType: "Bearer",
      expiresIn: 900,
      user: { id: "u-check", email: "check@example.com", fullName: "Check User" },
    });

    vi.spyOn(apiClient, "get").mockResolvedValueOnce({
      id: "u-check",
      email: "check@example.com",
      fullName: "Check User",
      customInstructions: "System level instructions.",
    });

    const authed = await useAuthStore.getState().checkAuth();

    expect(authed).toBe(true);
    const state = useAuthStore.getState();
    expect(state.user?.customInstructions).toBe("System level instructions.");
  });
});
