import { describe, expect, it, vi } from "vitest";
import { SessionManager } from "../src/lib/pronote/session-manager";
import type { PronoteCompatibilityLayer } from "../src/lib/pronote/data-source";
import type { StoredCredentials } from "../src/lib/pronote/secure-vault";
import type { PronoteSession } from "../src/lib/pronote/types";

function makeVault(initial: StoredCredentials | null) {
  let stored = initial;
  return {
    save: vi.fn(async (_id: string, creds: StoredCredentials) => {
      stored = creds;
    }),
    load: vi.fn(async () => stored),
    clear: vi.fn(async () => {
      stored = null;
    }),
    rotateKey: vi.fn(async () => {}),
  };
}

function makeConnector(overrides: Partial<PronoteCompatibilityLayer> = {}) {
  return {
    strategy: "DirectPronoteStrategy",
    authenticate: vi.fn(async () => ({
      token: "tok-new",
      expiresAt: Date.now() + 3_600_000,
      studentName: "Alex Martin",
      schoolName: "Lycée Jean Moulin",
      payload: { student: {}, subjects: [], grades: [], subjectAverages: [], generalAverage: null, periods: [] },
    })),
    restoreSession: vi.fn(async () => ({
      token: "tok-restored",
      expiresAt: Date.now() + 3_600_000,
      studentName: "Alex Martin",
      schoolName: "Lycée Jean Moulin",
      payload: { student: {}, subjects: [], grades: [], subjectAverages: [], generalAverage: null, periods: [] },
    })),
    fetchAll: vi.fn(),
    logout: vi.fn(async () => {}),
    ...overrides,
  } as unknown as PronoteCompatibilityLayer;
}

const session = (over: Partial<PronoteSession> = {}): PronoteSession => ({
  token: "tok",
  expiresAt: Date.now() + 3_600_000,
  schoolUrl: "https://x.index-education.net/pronote/",
  schoolName: "Lycée",
  lastSyncAt: Date.now(),
  connected: true,
  ...over,
});

describe("SessionManager.isSessionValid", () => {
  it("accepts a live session", () => {
    const manager = new SessionManager({ connector: makeConnector() });
    expect(manager.isSessionValid(session())).toBe(true);
  });

  it("rejects an expired session", () => {
    const manager = new SessionManager({ connector: makeConnector() });
    expect(manager.isSessionValid(session({ expiresAt: Date.now() - 1000 }))).toBe(false);
  });

  it("rejects a session that is about to expire within the skew window", () => {
    const manager = new SessionManager({ connector: makeConnector() });
    expect(manager.isSessionValid(session({ expiresAt: Date.now() + 10_000 }))).toBe(false);
  });

  it("rejects a disconnected session", () => {
    const manager = new SessionManager({ connector: makeConnector() });
    expect(manager.isSessionValid(session({ connected: false }))).toBe(false);
  });

  it("rejects a null session", () => {
    const manager = new SessionManager({ connector: makeConnector() });
    expect(manager.isSessionValid(null)).toBe(false);
  });
});

describe("SessionManager.restoreSession", () => {
  it("restores via the stored token and never touches the password", async () => {
    const vault = makeVault({
      schoolUrl: "https://x.index-education.net/pronote/",
      schoolName: "Lycée",
      username: "alex",
      token: "tok-restored",
      expiresAt: Date.now() + 3_600_000,
    });
    const connector = makeConnector();
    const manager = new SessionManager({ connector, vault });

    const result = await manager.restoreSession("acct-1");
    expect(result).not.toBeInstanceOf(Error);
    expect(connector.restoreSession).toHaveBeenCalledOnce();
    expect(connector.authenticate).not.toHaveBeenCalled();
  });

  it("returns a typed error when nothing is stored", async () => {
    const manager = new SessionManager({ connector: makeConnector(), vault: makeVault(null) });
    const result = await manager.restoreSession("acct-1");
    expect(result).toBeInstanceOf(Error);
    expect((result as Error).message).toBe("SessionExpired");
  });

  it("surfaces a typed error when the token is refused", async () => {
    const connector = makeConnector({
      restoreSession: vi.fn(async () => {
        throw new Error("SessionExpired");
      }),
    });
    const vault = makeVault({
      schoolUrl: "https://x.index-education.net/pronote/",
      schoolName: "Lycée",
      username: "alex",
      token: "stale",
    });
    const manager = new SessionManager({ connector, vault });
    const result = await manager.restoreSession("acct-1");
    expect(result).toBeInstanceOf(Error);
  });

  it("falls back to the password only when the connector has no token", async () => {
    const connector = makeConnector();
    const vault = makeVault({
      schoolUrl: "https://x.index-education.net/pronote/",
      schoolName: "Lycée",
      username: "alex",
      password: "secret",
    });
    const manager = new SessionManager({ connector, vault });
    await manager.restoreSession("acct-1");
    expect(connector.authenticate).toHaveBeenCalledOnce();
  });
});

describe("SessionManager.refreshSession", () => {
  it("returns null when the session cannot be salvaged", async () => {
    const manager = new SessionManager({ connector: makeConnector(), vault: makeVault(null) });
    expect(await manager.refreshSession("acct-1")).toBeNull();
  });

  it("returns a session when the token still works", async () => {
    const vault = makeVault({
      schoolUrl: "https://x.index-education.net/pronote/",
      schoolName: "Lycée",
      username: "alex",
      token: "tok-restored",
    });
    const manager = new SessionManager({ connector: makeConnector(), vault });
    const result = await manager.refreshSession("acct-1");
    expect(result?.connected).toBe(true);
  });
});

describe("SessionManager.clearSession", () => {
  it("wipes the vault entry", async () => {
    const vault = makeVault({
      schoolUrl: "https://x.index-education.net/pronote/",
      schoolName: "Lycée",
      username: "alex",
      token: "tok",
    });
    const manager = new SessionManager({ connector: makeConnector(), vault });
    await manager.clearSession("acct-1");
    expect(vault.clear).toHaveBeenCalledWith("acct-1");
  });
});
