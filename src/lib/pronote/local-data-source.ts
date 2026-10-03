import type { SchoolSnapshot } from "./types";
import { logger } from "./logger";

/**
 * LocalDataSource — the web stand-in for Room.
 *
 * Every school record is scoped to an account id, and every read goes through
 * this module so the UI can be served instantly from cache and refreshed
 * silently afterwards (cache-first + background refresh).
 *
 * NOTE: school data is intentionally kept out of any server. It never leaves the
 * device, and clearing the account removes it entirely.
 */

const PREFIX = "pronote-clay:snapshot:";
const PREF_PREFIX = "pronote-clay:prefs:";

export interface LocalPreferences {
  autoSync: boolean;
  notifyNewGrades: boolean;
  notifyAverageChange: boolean;
  theme: "system" | "light" | "dark";
  dynamicColor: boolean;
  biometricLock: boolean;
  hideInTaskSwitcher: boolean;
}

export const DEFAULT_PREFERENCES: LocalPreferences = {
  autoSync: true,
  notifyNewGrades: true,
  notifyAverageChange: true,
  theme: "system",
  dynamicColor: true,
  biometricLock: false,
  hideInTaskSwitcher: true,
};

function snapshotKey(accountId: string) {
  return `${PREFIX}${accountId}`;
}

function prefsKey(accountId: string) {
  return `${PREF_PREFIX}${accountId}`;
}

export const LocalDataSource = {
  loadSnapshot(accountId: string): SchoolSnapshot | null {
    try {
      const raw = localStorage.getItem(snapshotKey(accountId));
      if (!raw) return null;
      return JSON.parse(raw) as SchoolSnapshot;
    } catch {
      logger.warn("Local snapshot unreadable");
      return null;
    }
  },

  saveSnapshot(accountId: string, snapshot: SchoolSnapshot): void {
    try {
      localStorage.setItem(snapshotKey(accountId), JSON.stringify(snapshot));
    } catch {
      logger.warn("Local snapshot write failed");
    }
  },

  clearSnapshot(accountId: string): void {
    try {
      localStorage.removeItem(snapshotKey(accountId));
    } catch {
      /* ignore */
    }
  },

  loadPreferences(accountId: string): LocalPreferences {
    try {
      const raw = localStorage.getItem(prefsKey(accountId));
      if (!raw) return DEFAULT_PREFERENCES;
      return { ...DEFAULT_PREFERENCES, ...(JSON.parse(raw) as Partial<LocalPreferences>) };
    } catch {
      return DEFAULT_PREFERENCES;
    }
  },

  savePreferences(accountId: string, prefs: Partial<LocalPreferences>): void {
    try {
      const next = { ...LocalDataSource.loadPreferences(accountId), ...prefs };
      localStorage.setItem(prefsKey(accountId), JSON.stringify(next));
    } catch {
      logger.warn("Local preferences write failed");
    }
  },

  clearPreferences(accountId: string): void {
    try {
      localStorage.removeItem(prefsKey(accountId));
    } catch {
      /* ignore */
    }
  },

  /** "Supprimer mes données" — wipes every trace of the account on this device. */
  clearAccount(accountId: string): void {
    LocalDataSource.clearSnapshot(accountId);
    LocalDataSource.clearPreferences(accountId);
  },
};
