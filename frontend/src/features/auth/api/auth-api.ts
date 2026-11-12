import { apiClient } from "@/lib/api-client";
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  UpdateProfileRequest,
  User,
} from "../types";

export const authApi = {
  login: (request: LoginRequest) => apiClient.post<AuthResponse>("/api/auth/login", request),
  register: (request: RegisterRequest) => apiClient.post<unknown>("/api/auth/register", request),
  /** Exchanges the HTTP-only refresh cookie for a new access token. */
  refresh: () => apiClient.post<AuthResponse>("/api/auth/refresh"),
  logout: () => apiClient.post<void>("/api/auth/logout"),
  getProfile: () => apiClient.get<User>("/api/auth/me"),
  updateProfile: (request: UpdateProfileRequest) => apiClient.patch<User>("/api/auth/me", request),
};
