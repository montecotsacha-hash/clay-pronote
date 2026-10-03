import { logger } from "./logger";
import type { PronoteRepository } from "./repository";
import type { Grade, SchoolSnapshot } from "./types";

/**
 * SyncManager — the WorkManager equivalent.
 *
 * Guarantees:
 *  - never two concurrent syncs (mutex + unique work name)
 *  - never sync when offline
 *  - skip when the last sync is recent, unless the caller forces it
 *  - detect new grades and average changes after every successful sync
 */

export const PERIODIC_SYNC_WORK_NAME = "pronote_periodic_sync";

/** Minimum delay between two automatic syncs. */
export const MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000;

export interface SyncResult {
  ok: boolean;
  snapshot?: SchoolSnapshot;
  newGrades: Grade[];
  previousAverage: number | null;
  nextAverage: number | null;
  skipped?: "offline" | "recent" | "busy";
  error?: string;
}

export interface SyncManagerDeps {
  repository: PronoteRepository;
  isOnline: () => boolean;
  now?: () => number;
}

export class SyncManager {
  private mutex: Promise<SyncResult> | null = null;
  private lastSyncAt = 0;
  private timer: ReturnType<typeof setInterval> | null = null;
  private readonly deps: SyncManagerDeps;

  constructor(deps: SyncManagerDeps) {
    this.deps = deps;
  }

  get isSyncing(): boolean {
    return this.mutex !== null;
  }

  get lastSync(): number {
    return this.lastSyncAt;
  }

  /**
   * Run a sync. `force` bypasses the "recent sync" guard (the "Actualiser"
   * button), but never bypasses the offline check or the mutex.
   */
  async sync(accountId: string, options: { force?: boolean } = {}): Promise<SyncResult> {
    if (!this.deps.isOnline()) {
      logger.info("Sync skipped: offline");
      return { ok: false, skipped: "offline", newGrades: [], previousAverage: null, nextAverage: null };
    }

    if (this.mutex) {
      logger.info("Sync skipped: already running");
      return { ok: false, skipped: "busy", newGrades: [], previousAverage: null, nextAverage: null };
    }

    const now = this.deps.now?.() ?? Date.now();
    if (!options.force && now - this.lastSyncAt < MIN_SYNC_INTERVAL_MS) {
      logger.info("Sync skipped: recent");
      return { ok: false, skipped: "recent", newGrades: [], previousAverage: null, nextAverage: null };
    }

    this.mutex = this.run(accountId, now);
    try {
      return await this.mutex;
    } finally {
      this.mutex = null;
    }
  }

  private async run(accountId: string, startedAt: number): Promise<SyncResult> {
    const before = this.deps.repository.getCachedSnapshot(accountId);
    const previousAverage = before?.generalAverage?.value ?? null;

    logger.info("Sync started");
    const result = await this.deps.repository.sync(accountId);

    if (result instanceof Error) {
      logger.warn("Sync failed");
      return {
        ok: false,
        newGrades: [],
        previousAverage,
        nextAverage: previousAverage,
        error: result.message,
      };
    }

    this.lastSyncAt = this.deps.now?.() ?? Date.now();
    void startedAt;
    const newGrades = detectNewGrades(before, result);

    return {
      ok: true,
      snapshot: result,
      newGrades,
      previousAverage,
      nextAverage: result.generalAverage?.value ?? null,
    };
  }

  /** Start periodic auto-sync. Repeated calls reuse the same timer. */
  startPeriodic(accountId: string, intervalMs = MIN_SYNC_INTERVAL_MS): void {
    this.stopPeriodic();
    logger.info("Sync started", { work: PERIODIC_SYNC_WORK_NAME });
    this.timer = setInterval(() => {
      void this.sync(accountId);
    }, intervalMs);
  }

  stopPeriodic(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  /** Called when a session is replaced so the next sync is never skipped. */
  reset(): void {
    this.lastSyncAt = 0;
  }
}

/** Compare the previous grade list with the new one. */
export function detectNewGrades(
  before: SchoolSnapshot | null,
  after: SchoolSnapshot,
): Grade[] {
  if (!before) return [];
  const known = new Set(before.grades.map((g) => g.id));
  return after.grades.filter((g) => !known.has(g.id));
}
