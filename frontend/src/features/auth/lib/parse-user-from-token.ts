import type { User } from "../types";

interface TokenClaims {
  sub?: string;
  userId?: string;
  email?: string;
  fullName?: string;
}

/** Reads the user from the JWT payload; used when the refresh response carries no user. */
export function parseUserFromToken(token: string): User | null {
  const payload = token.split(".")[1];
  if (!payload) {
    return null;
  }
  try {
    const claims = JSON.parse(decodeBase64Url(payload)) as TokenClaims;
    return {
      id: claims.userId || claims.sub || "",
      email: claims.email || "",
      fullName: claims.fullName || "",
    };
  } catch {
    return null;
  }
}

function decodeBase64Url(value: string): string {
  const binary = atob(value.replace(/-/g, "+").replace(/_/g, "/"));
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}
