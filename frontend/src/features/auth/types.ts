/** Mirrors the backend `UserSummary`. */
export interface User {
  id: string;
  email: string;
  fullName: string;
  customInstructions?: string | null;
}

/** Mirrors the backend `AuthResponse` (login) and `RefreshTokenResponse` (refresh, no user). */
export interface AuthResponse {
  accessToken: string;
  tokenType?: string;
  expiresIn?: number;
  user?: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface UpdateProfileRequest {
  fullName?: string;
  customInstructions?: string;
}
