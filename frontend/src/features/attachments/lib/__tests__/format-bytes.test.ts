import { describe, expect, it } from "vitest";
import { formatBytes } from "../format-bytes";

describe("formatBytes", () => {
  it.each([
    [undefined, "0 B"],
    [0, "0 B"],
    [512, "512 B"],
    [10240, "10 KB"],
    [1536, "1.5 KB"],
    [5 * 1024 ** 3, "5 GB"],
    [3 * 1024 ** 4, "3072 GB"],
  ])("%s -> %s", (bytes, expected) => {
    expect(formatBytes(bytes)).toBe(expected);
  });
});
