import { type ApiError, SESSION_EXPIRED_MESSAGE, isAbortError } from "./api-error";

const REFRESH_ENDPOINT = "/api/auth/refresh";
// These endpoints answer 401 for bad credentials, not for an expired access token.
const ENDPOINTS_WITHOUT_REFRESH = ["/api/auth/login", "/api/auth/register", REFRESH_ENDPOINT];
const NETWORK_ERROR_MESSAGE = "Network error. Please check your connection.";

export interface RequestOptions extends RequestInit {
  /** Set on the single retry after a token refresh so a second 401 is not retried again. */
  isRetry?: boolean;
}

interface ErrorBody {
  message?: string;
  errors?: Record<string, string>;
}

/**
 * Fetch wrapper for the LocalMind backend: adds the bearer token and the refresh-token cookie,
 * refreshes the access token once on 401 (concurrent callers share one refresh) and turns
 * failures into {@link ApiError}.
 */
class ApiClient {
  private accessToken: string | null = null;
  private refreshInFlight: Promise<string> | null = null;
  private authFailureListener: (() => void) | null = null;

  public setAccessToken(token: string | null): void {
    this.accessToken = token;
  }

  public getAccessToken(): string | null {
    return this.accessToken;
  }

  /** Called when the refresh token is rejected and the user must sign in again. */
  public onAuthFailure(listener: () => void): void {
    this.authFailureListener = listener;
  }

  private async request<T = unknown>(endpoint: string, options: RequestOptions = {}): Promise<T> {
    const response = await this.send(endpoint, options);
    return parseBody<T>(response);
  }

  public get<T = unknown>(endpoint: string, options?: RequestOptions): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: "GET" });
  }

  public post<T = unknown>(endpoint: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: "POST", body: toRequestBody(body) });
  }

  public patch<T = unknown>(
    endpoint: string,
    body?: unknown,
    options?: RequestOptions
  ): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: "PATCH", body: toRequestBody(body) });
  }

  public delete<T = unknown>(endpoint: string, options?: RequestOptions): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: "DELETE" });
  }

  /** POSTs JSON and returns the raw response so the caller can read a server-sent event stream. */
  public stream(endpoint: string, body: unknown, signal?: AbortSignal): Promise<Response> {
    return this.send(endpoint, {
      method: "POST",
      headers: { Accept: "text/event-stream" },
      body: toRequestBody(body),
      signal,
    });
  }

  private async send(endpoint: string, options: RequestOptions): Promise<Response> {
    const { isRetry = false, ...init } = options;
    const response = await this.fetchWithAuth(endpoint, init);

    if (response.status === 401 && !isRetry && !ENDPOINTS_WITHOUT_REFRESH.includes(endpoint)) {
      await this.refreshAccessToken();
      return this.send(endpoint, { ...init, isRetry: true });
    }
    if (!response.ok) {
      throw await toApiError(response);
    }
    return response;
  }

  private async fetchWithAuth(endpoint: string, init: RequestInit): Promise<Response> {
    const headers = new Headers(init.headers);
    if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    if (this.accessToken && !headers.has("Authorization")) {
      headers.set("Authorization", `Bearer ${this.accessToken}`);
    }

    try {
      // credentials: the refresh token travels in an HTTP-only cookie.
      return await fetch(endpoint, { ...init, headers, credentials: "include" });
    } catch (error) {
      if (isAbortError(error)) {
        throw error;
      }
      const networkError: ApiError = {
        status: 0,
        message: error instanceof Error && error.message ? error.message : NETWORK_ERROR_MESSAGE,
      };
      throw networkError;
    }
  }

  private refreshAccessToken(): Promise<string> {
    this.refreshInFlight ??= this.performRefresh().finally(() => {
      this.refreshInFlight = null;
    });
    return this.refreshInFlight;
  }

  private async performRefresh(): Promise<string> {
    try {
      const response = await fetch(REFRESH_ENDPOINT, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
      });
      if (!response.ok) {
        throw new Error("Refresh token expired or invalid");
      }
      const { accessToken } = (await response.json()) as { accessToken: string };
      this.accessToken = accessToken;
      return accessToken;
    } catch {
      this.accessToken = null;
      this.authFailureListener?.();
      const sessionExpired: ApiError = { status: 401, message: SESSION_EXPIRED_MESSAGE };
      throw sessionExpired;
    }
  }
}

function toRequestBody(body: unknown): BodyInit | undefined {
  if (body === undefined || body === null) {
    return undefined;
  }
  return body instanceof FormData ? body : JSON.stringify(body);
}

async function parseBody<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return {} as T;
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    return (await response.json()) as T;
  }
  return (await response.text()) as unknown as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: ErrorBody | null = null;
  try {
    body = (await response.json()) as ErrorBody;
  } catch {
    // Non-JSON error bodies (proxy pages, empty bodies) fall back to the status text.
  }
  const fallback = body
    ? `HTTP ${response.status}: ${response.statusText}`
    : response.statusText || `Request failed with status ${response.status}`;
  return { status: response.status, message: body?.message || fallback, errors: body?.errors };
}

export const apiClient = new ApiClient();
