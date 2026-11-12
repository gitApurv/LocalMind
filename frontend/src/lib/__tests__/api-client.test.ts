import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { apiClient } from "../api-client";

describe("ApiClient", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    apiClient.setAccessToken(null);
    vi.restoreAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it("should set and retrieve access token", () => {
    expect(apiClient.getAccessToken()).toBeNull();
    apiClient.setAccessToken("test-token-123");
    expect(apiClient.getAccessToken()).toBe("test-token-123");
  });

  it("should include Authorization header when token is set", async () => {
    apiClient.setAccessToken("jwt-abc-xyz");

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => ({ message: "success" }),
    });
    global.fetch = mockFetch;

    const res = await apiClient.get("/api/test");
    expect(res).toEqual({ message: "success" });
    expect(mockFetch).toHaveBeenCalledWith(
      "/api/test",
      expect.objectContaining({
        credentials: "include",
        headers: expect.any(Headers),
      })
    );

    const callHeaders: Headers = mockFetch.mock.calls[0][1].headers;
    expect(callHeaders.get("Authorization")).toBe("Bearer jwt-abc-xyz");
  });

  it("should parse JSON response and handle 204 No Content", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      headers: new Headers(),
    });
    global.fetch = mockFetch;

    const res = await apiClient.delete("/api/items/1");
    expect(res).toEqual({});
  });

  it("should throw ApiError with message on HTTP error", async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      statusText: "Bad Request",
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => ({ message: "Email is already taken" }),
    });
    global.fetch = mockFetch;

    await expect(apiClient.post("/api/auth/register", {})).rejects.toMatchObject({
      status: 400,
      message: "Email is already taken",
    });
  });

  it("should intercept 401, refresh token, and retry original request", async () => {
    apiClient.setAccessToken("old-expired-token");

    let callCount = 0;
    const mockFetch = vi.fn().mockImplementation((url) => {
      callCount++;
      if (url === "/api/protected" && callCount === 1) {
        // Initial request fails with 401
        return Promise.resolve({
          ok: false,
          status: 401,
          statusText: "Unauthorized",
          headers: new Headers(),
          json: async () => ({ message: "Expired token" }),
        });
      } else if (url === "/api/auth/refresh") {
        // Refresh token request succeeds
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: new Headers({ "content-type": "application/json" }),
          json: async () => ({ accessToken: "new-fresh-token" }),
        });
      } else if (url === "/api/protected" && callCount === 3) {
        // Retried original request succeeds
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: new Headers({ "content-type": "application/json" }),
          json: async () => ({ data: "secret data" }),
        });
      }
      return Promise.reject(new Error("Unexpected request: " + url));
    });
    global.fetch = mockFetch;

    const result = await apiClient.get("/api/protected");
    expect(result).toEqual({ data: "secret data" });
    expect(apiClient.getAccessToken()).toBe("new-fresh-token");
    expect(mockFetch).toHaveBeenCalledTimes(3);
  });

  it("should reject queued concurrent requests when token refresh fails", async () => {
    let refreshAttempts = 0;
    const mockFetch = vi.fn().mockImplementation((url) => {
      if (url === "/api/auth/refresh") {
        refreshAttempts++;
        return Promise.resolve({
          ok: false,
          status: 401,
          statusText: "Unauthorized",
          headers: new Headers(),
          json: async () => ({ message: "Session expired" }),
        });
      }
      return Promise.resolve({
        ok: false,
        status: 401,
        statusText: "Unauthorized",
        headers: new Headers(),
        json: async () => ({ message: "Unauthorized" }),
      });
    });
    global.fetch = mockFetch;

    // Dispatch two concurrent requests
    const req1 = apiClient.get("/api/res1");
    const req2 = apiClient.get("/api/res2");

    await expect(req1).rejects.toMatchObject({
      status: 401,
      message: "Session expired. Please log in again.",
    });
    await expect(req2).rejects.toMatchObject({
      status: 401,
      message: "Session expired. Please log in again.",
    });

    expect(refreshAttempts).toBe(1);
    expect(apiClient.getAccessToken()).toBeNull();
  });

  it("should not infinitely loop if retried request still returns 401", async () => {
    let callCount = 0;
    const mockFetch = vi.fn().mockImplementation((url) => {
      callCount++;
      if (url === "/api/auth/refresh") {
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: new Headers({ "content-type": "application/json" }),
          json: async () => ({ accessToken: "fresh-token-123" }),
        });
      }
      // Protected endpoint always returns 401
      return Promise.resolve({
        ok: false,
        status: 401,
        statusText: "Unauthorized",
        headers: new Headers(),
        json: async () => ({ message: "Still unauthorized" }),
      });
    });
    global.fetch = mockFetch;

    await expect(apiClient.get("/api/always-401")).rejects.toMatchObject({
      status: 401,
    });

    // Should call /api/always-401, then /api/auth/refresh, then /api/always-401 once more and STOP
    expect(callCount).toBe(3);
  });

  it("should turn a fetch failure into an ApiError with status 0", async () => {
    global.fetch = vi.fn().mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(apiClient.get("/api/conversations")).rejects.toEqual({
      status: 0,
      message: "Failed to fetch",
    });
  });

  it("should rethrow AbortError untouched", async () => {
    const abort = new DOMException("The user aborted a request.", "AbortError");
    global.fetch = vi.fn().mockRejectedValue(abort);

    await expect(apiClient.get("/api/conversations")).rejects.toBe(abort);
  });

  it("should fall back to the status text when the error body is not JSON", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      statusText: "Bad Gateway",
      headers: new Headers({ "content-type": "text/html" }),
      json: async () => {
        throw new SyntaxError("Unexpected token <");
      },
    });

    await expect(apiClient.get("/api/models")).rejects.toMatchObject({
      status: 502,
      message: "Bad Gateway",
    });
  });

  it("stream() should refresh an expired token and retry before returning the response", async () => {
    apiClient.setAccessToken("expired");
    const body = new ReadableStream();
    const mockFetch = vi
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 401, statusText: "Unauthorized" })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        json: async () => ({ accessToken: "fresh" }),
      })
      .mockResolvedValueOnce({ ok: true, status: 200, body });
    global.fetch = mockFetch;

    const response = await apiClient.stream("/api/chat/stream", { message: "hi" });

    expect(response.body).toBe(body);
    expect(mockFetch.mock.calls.map(([url]) => url)).toEqual([
      "/api/chat/stream",
      "/api/auth/refresh",
      "/api/chat/stream",
    ]);
    const retryHeaders: Headers = mockFetch.mock.calls[2][1].headers;
    expect(retryHeaders.get("Authorization")).toBe("Bearer fresh");
    expect(mockFetch.mock.calls[2][1].body).toBe(JSON.stringify({ message: "hi" }));
  });
});
