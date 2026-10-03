/**
 * Internal logger.
 *
 * Allowed: "Sync started", "Sync completed", "Grades updated".
 * Forbidden: password, token, session, personal data, named grades.
 *
 * `sanitize` strips anything that looks like a secret before it is printed, and
 * production builds only keep warn/error output.
 */

const REDACTED = "[redacted]";

const SECRET_KEYS = [
  "password",
  "passwd",
  "token",
  "session",
  "sessionid",
  "secret",
  "credential",
  "authorization",
  "apikey",
];

const DEBUG = import.meta.env.DEV;

export function sanitize(value: unknown): unknown {
  if (value == null) return value;
  if (typeof value === "string") {
    // never let a raw token-shaped string through
    return value.length > 24 ? `${value.slice(0, 6)}…${REDACTED}` : value;
  }
  if (typeof value !== "object") return value;
  if (Array.isArray(value)) return value.map(sanitize);

  const out: Record<string, unknown> = {};
  for (const [key, val] of Object.entries(value as Record<string, unknown>)) {
    out[key] = SECRET_KEYS.some((s) => key.toLowerCase().includes(s))
      ? REDACTED
      : sanitize(val);
  }
  return out;
}

function emit(level: "debug" | "info" | "warn" | "error", message: string, meta?: unknown) {
  if (!DEBUG && level === "debug") return;
  const payload = meta === undefined ? "" : sanitize(meta);
  const line = `[pronote] ${message}`;
  if (level === "error") console.error(line, payload);
  else if (level === "warn") console.warn(line, payload);
  else console.info(line, payload);
}

export const logger = {
  debug: (message: string, meta?: unknown) => emit("debug", message, meta),
  info: (message: string, meta?: unknown) => emit("info", message, meta),
  warn: (message: string, meta?: unknown) => emit("warn", message, meta),
  error: (message: string, meta?: unknown) => emit("error", message, meta),
};
