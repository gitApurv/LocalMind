import { describe, expect, it } from "vitest";
import { validateLogin, validateRegister } from "../validation";

describe("validateLogin", () => {
  it.each([
    [{ email: "", password: "x" }, "Email address is required"],
    [{ email: "not-an-email", password: "x" }, "Please enter a valid email address"],
    [{ email: "a@b.co", password: "" }, "Password is required"],
    [{ email: "a@b.co", password: "x" }, null],
  ])("%o -> %s", (fields, expected) => {
    expect(validateLogin(fields)).toBe(expected);
  });
});

describe("validateRegister", () => {
  it.each([
    [{ fullName: " ", email: "a@b.co", password: "12345678" }, "Full name is required"],
    [
      { fullName: "A", email: "a@b.co", password: "12345678" },
      "Name must be at least 2 characters",
    ],
    [{ fullName: "Al", email: "bad", password: "12345678" }, "Please enter a valid email address"],
    [
      { fullName: "Al", email: "a@b.co", password: "1234567" },
      "Password must be at least 8 characters long",
    ],
    [{ fullName: "Al", email: "a@b.co", password: "12345678" }, null],
    [{ fullName: "Al", email: "a@b.co", password: "x".repeat(72) }, null],
    [
      { fullName: "Al", email: "a@b.co", password: "x".repeat(73) },
      "Password cannot exceed 72 characters",
    ],
  ])("%o -> %s", (fields, expected) => {
    expect(validateRegister(fields)).toBe(expected);
  });
});
