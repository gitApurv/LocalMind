const UNITS = ["B", "KB", "MB", "GB"];

/** Formats a byte count with one decimal, e.g. 10240 -> "10 KB". */
export function formatBytes(bytes?: number): string {
  if (!bytes) return "0 B";
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), UNITS.length - 1);
  return `${parseFloat((bytes / 1024 ** exponent).toFixed(1))} ${UNITS[exponent]}`;
}
