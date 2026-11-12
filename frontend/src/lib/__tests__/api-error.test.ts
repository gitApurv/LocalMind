import { describe, expect, it } from "vitest";
import { getErrorMessage, isAbortError } from "../api-error";

describe("api-error", () => {
  it("getErrorMessage() reads message from ApiError and Error, else falls back", () => {
    expect(getErrorMessage({ status: 400, message: "Bad input" }, "fallback")).toBe("Bad input");
    expect(getErrorMessage(new Error("boom"), "fallback")).toBe("boom");
    expect(getErrorMessage({ message: "" }, "fallback")).toBe("fallback");
    expect(getErrorMessage("oops", "fallback")).toBe("fallback");
    expect(getErrorMessage(null, "fallback")).toBe("fallback");
  });

  it("isAbortError() recognises aborted requests only", () => {
    expect(isAbortError(new DOMException("stop", "AbortError"))).toBe(true);
    expect(isAbortError(new Error("x"))).toBe(false);
  });
});
