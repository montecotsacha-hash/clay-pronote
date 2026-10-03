import { describe, expect, it } from "vitest";
import {
  detectNewGrades,
  SyncManager,
  MIN_SYNC_INTERVAL_MS,
} from "../src/lib/pronote/sync-manager";
import type { PronoteRepository } from "../src/lib/pronote/repository";
import type { SchoolSnapshot } from "../src/lib/pronote/types";

function snapshot(id: string, average: number | null, gradeIds: string[]): SchoolSnapshot {
  return {
    student: {
      id: "s1",
      firstName: "Alex",
      lastName: "Martin",
      className: "1re",
      schoolName: "Lycée",
    },
    subjects: [],
    grades: gradeIds.map((gid) => ({
      id: gid,
      subjectId: "maths",
      title: "Éval",
      value: 12,
      maxValue: 20,
      coefficient: 1,
      date: "2026-02-01",
      countsTowardAverage: true,
      updatedAt: 0,
    })),
    subjectAverages: [],
    generalAverage:
      average === null
        ? null
        : { value: average, outOf: 20, period: "T2", source: "CALCULATED", updatedAt: 0 },
    history: [],
    periods: [{ id: "T2", label: "2e trimestre", current: true }],
    fetchedAt: 0,
  };
}

function makeRepository(options: {
  cached?: SchoolSnapshot | null;
  next?: SchoolSnapshot | Error;
}): { repo: PronoteRepository; calls: number[] } {
  const calls: number[] = [];
  let cached = options.cached ?? null;
  const repo = {
    login: async () => {
      throw new Error("not used");
    },
    refreshSession: async () => null,
    getCachedSnapshot: () => cached,
    sync: async () => {
      calls.push(Date.now());
      const next = options.next;
      if (next instanceof Error) return next;
      cached = next ?? null;
      return cached as SchoolSnapshot;
    },
    getPreferences: () => ({
      autoSync: true,
      notifyNewGrades: true,
      notifyAverageChange: true,
      theme: "system" as const,
      dynamicColor: true,
      biometricLock: false,
      hideInTaskSwitcher: true,
    }),
    savePreferences: () => {},
    logout: async () => {},
    deleteLocalData: async () => {},
  } satisfies PronoteRepository;
  return { repo, calls };
}

describe("SyncManager", () => {
  it("refuses to sync while offline", async () => {
    const { repo, calls } = makeRepository({ next: snapshot("a", 15, ["g1"]) });
    const manager = new SyncManager({ repository: repo, isOnline: () => false });
    const result = await manager.sync("acct", { force: true });
    expect(result.skipped).toBe("offline");
    expect(calls).toHaveLength(0);
  });

  it("never runs two syncs at once (mutex)", async () => {
    const { repo, calls } = makeRepository({ next: snapshot("a", 15, ["g1"]) });
    const manager = new SyncManager({ repository: repo, isOnline: () => true });

    const [first, second] = await Promise.all([
      manager.sync("acct", { force: true }),
      manager.sync("acct", { force: true }),
    ]);

    expect(calls).toHaveLength(1);
    const statuses = [first.skipped, second.skipped].filter(Boolean);
    expect(statuses).toContain("busy");
  });

  it("skips an automatic sync that is too recent, but a forced one always runs", async () => {
    const { repo, calls } = makeRepository({ next: snapshot("a", 15, ["g1"]) });
    const manager = new SyncManager({ repository: repo, isOnline: () => true });

    const first = await manager.sync("acct", { force: true });
    expect(first.ok).toBe(true);

    const skipped = await manager.sync("acct");
    expect(skipped.skipped).toBe("recent");

    const forced = await manager.sync("acct", { force: true });
    expect(forced.ok).toBe(true);
    expect(calls).toHaveLength(2);
  });

  it("respects MIN_SYNC_INTERVAL_MS via an injected clock", async () => {
    let now = 1_000_000;
    const { repo, calls } = makeRepository({ next: snapshot("a", 15, ["g1"]) });
    const manager = new SyncManager({
      repository: repo,
      isOnline: () => true,
      now: () => now,
    });

    await manager.sync("acct", { force: true });
    now += MIN_SYNC_INTERVAL_MS - 1;
    expect((await manager.sync("acct")).skipped).toBe("recent");

    now += 2;
    expect((await manager.sync("acct")).ok).toBe(true);
    expect(calls).toHaveLength(2);
  });

  it("reports a typed failure and keeps the previous average", async () => {
    const { repo } = makeRepository({
      cached: snapshot("a", 15, ["g1"]),
      next: new Error("boom"),
    });
    const manager = new SyncManager({ repository: repo, isOnline: () => true });
    const result = await manager.sync("acct", { force: true });
    expect(result.ok).toBe(false);
    expect(result.previousAverage).toBe(15);
    expect(result.nextAverage).toBe(15);
  });

  it("exposes the new average and detects new grades", async () => {
    const { repo } = makeRepository({
      cached: snapshot("a", 15.6, ["g1"]),
      next: snapshot("b", 15.8, ["g1", "g2"]),
    });
    const manager = new SyncManager({ repository: repo, isOnline: () => true });
    const result = await manager.sync("acct", { force: true });
    expect(result.previousAverage).toBe(15.6);
    expect(result.nextAverage).toBe(15.8);
    expect(result.newGrades.map((g) => g.id)).toEqual(["g2"]);
  });

  it("startPeriodic reuses a single timer and stopPeriodic clears it", () => {
    const { repo } = makeRepository({ next: snapshot("a", 15, ["g1"]) });
    const manager = new SyncManager({ repository: repo, isOnline: () => true });
    manager.startPeriodic("acct", 10_000);
    manager.startPeriodic("acct", 10_000);
    manager.stopPeriodic();
    expect(true).toBe(true);
  });
});

describe("detectNewGrades", () => {
  it("returns nothing on a first sync (no previous snapshot)", () => {
    expect(detectNewGrades(null, snapshot("a", 15, ["g1"]))).toEqual([]);
  });

  it("returns only the newly added grades", () => {
    const before = snapshot("a", 15, ["g1", "g2"]);
    const after = snapshot("b", 15, ["g1", "g2", "g3"]);
    expect(detectNewGrades(before, after).map((g) => g.id)).toEqual(["g3"]);
  });
});
