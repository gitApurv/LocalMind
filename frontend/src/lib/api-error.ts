/** Error shape thrown by `apiClient` for every failed request. */
export interface ApiError {
  /** HTTP status, or 0 when the request never reached the server. */
  status: number;
  /** Human-readable text, taken from the backend ProblemDetail `message` property. */
  message: string;
  /** Field-level validation errors, when the backend sends them. */
  errors?: Record<string, string>;
}

export const SESSION_EXPIRED_MESSAGE = "Session expired. Please log in again.";

/** Reads a displayable message from anything thrown, falling back when there is none. */
export function getErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === "object" && error !== null && "message" in error) {
    const { message } = error;
    if (typeof message === "string" && message) {
      return message;
    }
  }
  return fallback;
}

export function isAbortError(error: unknown): boolean {
  return (
    typeof error === "object" && error !== null && "name" in error && error.name === "AbortError"
  );
}
