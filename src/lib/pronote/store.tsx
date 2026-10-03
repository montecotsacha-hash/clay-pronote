import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { useAuth } from "@/hooks/use-auth";
import { container } from "./container";
import type { LocalPreferences } from "./local-data-source";
import { logger } from "./logger";
import { useNetworkMonitor } from "./network-monitor";
import {
  averageChangeNotification,
  newGradeNotification,
  notify,
} from "./notifications";
import type { PronoteError } from "./errors";
import type { PronoteSession, SchoolSnapshot } from "./types";

/**
 * PronoteProvider — the ViewModel layer.
 *
 * Explicit UI states (`loading` | `success` | `refreshing` | `offline` | `error`
 * | `unauthenticated`) with a cache-first strategy: cached data is rendered
 * immediately, then refreshed silently in the background.
 */

export type HomeUiState =
  | { status: "loading" }
  | { status: "unauthenticated" }
  | { status: "success"; snapshot: SchoolSnapshot; refreshing: boolean }
  | { status: "offline"; snapshot: SchoolSnapshot | null }
  | { status: "error"; error: string; hint?: string; snapshot: SchoolSnapshot | null };

interface PronoteContextValue {
  state: HomeUiState;
  session: PronoteSession | null;
  snapshot: SchoolSnapshot | null;
  accountId: string | null;
  preferences: LocalPreferences;
  online: boolean;
  syncing: boolean;
  login: (params: {
    accountId: string;
    schoolUrl: string;
    username: string;
    password: string;
  }) => Promise<{ ok: true } | { ok: false; error: PronoteError }>;
  logout: () => Promise<void>;
  refresh: (options?: { force?: boolean }) => Promise<void>;
  updatePreferences: (prefs: Partial<LocalPreferences>) => void;
  deleteLocalData: () => Promise<void>;
  lastSyncError: string | null;
}

const PronoteContext = createContext<PronoteContextValue | null>(null);

const ACTIVE_ACCOUNT_KEY = "pronote-clay:active-account";
const GUEST_ACCOUNT = "guest";

function readActiveAccount(): string | null {
  try {
    return localStorage.getItem(ACTIVE_ACCOUNT_KEY);
  } catch {
    return null;
  }
}

function writeActiveAccount(accountId: string | null): void {
  try {
    if (accountId) localStorage.setItem(ACTIVE_ACCOUNT_KEY, accountId);
    else localStorage.removeItem(ACTIVE_ACCOUNT_KEY);
  } catch {
    /* non-fatal */
  }
}

export function PronoteProvider({
  children,
  accountId: accountIdProp,
  online,
}: {
  children: React.ReactNode;
  accountId: string | null;
  online: boolean;
}) {
  const accountId = accountIdProp ?? readActiveAccount();

  const [snapshot, setSnapshot] = useState<SchoolSnapshot | null>(null);
  const [session, setSession] = useState<PronoteSession | null>(null);
  const [preferences, setPreferences] = useState<LocalPreferences>(() =>
    container.repository.getPreferences(accountId ?? GUEST_ACCOUNT),
  );
  const [syncing, setSyncing] = useState(false);
  const [lastSyncError, setLastSyncError] = useState<string | null>(null);
  const [fatalError, setFatalError] = useState<{ error: string; hint?: string } | null>(null);
  const [restoreAttempted, setRestoreAttempted] = useState(false);

  // ---- step 1: load the local cache immediately --------------------------
  useEffect(() => {
    // Deferred to a microtask so hydrating from cache never triggers a
    // synchronous cascading render inside the effect.
    let cancelled = false;

    const hydrate = () => {
      if (cancelled) return;

      if (!accountId) {
        setSnapshot(null);
        setRestoreAttempted(true);
        return;
      }

      setSnapshot(container.repository.getCachedSnapshot(accountId));
      setPreferences(container.repository.getPreferences(accountId));
      setRestoreAttempted(false);

      // ---- step 2: restore the session, bounded so it can never hang ------//
      const timeout = new Promise<null>((resolve) => setTimeout(() => resolve(null), 2500));
      void Promise.race([container.useCases.refreshSession.execute(accountId), timeout])
        .then((restored) => {
          if (cancelled) return;
          if (restored && !(restored instanceof Error)) {
            setSession(restored);
          } else {
            logger.warn("Session could not be restored");
          }
        })
        .finally(() => {
          if (!cancelled) setRestoreAttempted(true);
        });
    };

    const handle = requestAnimationFrame(hydrate);
    return () => {
      cancelled = true;
      cancelAnimationFrame(handle);
    };
  }, [accountId]);

  // ---- derived UI state -------------------------------------------------
  const state = useMemo<HomeUiState>(() => {
    if (fatalError) return { status: "error", ...fatalError, snapshot };
    if (!accountId) return { status: "unauthenticated" };
    if (!snapshot) {
      return restoreAttempted ? { status: "unauthenticated" } : { status: "loading" };
    }
    if (!online) return { status: "offline", snapshot };
    return { status: "success", snapshot, refreshing: syncing };
  }, [fatalError, accountId, snapshot, online, syncing, restoreAttempted]);

  // ---- sync -------------------------------------------------------------
  const runSync = useCallback(
    async (options: { force?: boolean } = {}) => {
      if (!accountId || !snapshot) return;
      setSyncing(true);

      const result = await container.syncManager.sync(accountId, options);

      if (result.ok && result.snapshot) {
        setSnapshot(result.snapshot);
        setLastSyncError(null);

        // Local notifications only — nothing leaves the device.
        if (preferences.notifyNewGrades && result.newGrades.length > 0) {
          const grade = result.newGrades[0];
          const subject = result.snapshot.subjects.find((s) => s.id === grade.subjectId);
          notify("Nouvelle note", newGradeNotification(grade, subject));
        }
        if (
          preferences.notifyAverageChange &&
          result.previousAverage !== null &&
          result.nextAverage !== null &&
          Math.abs(result.nextAverage - result.previousAverage) >= 0.05
        ) {
          notify(
            "Ta moyenne a changé",
            averageChangeNotification(result.nextAverage, result.previousAverage),
          );
        }
      } else if (result.error) {
        setLastSyncError(result.error);
      }

      setSyncing(false);
    },
    [
      accountId,
      snapshot,
      preferences.notifyAverageChange,
      preferences.notifyNewGrades,
    ],
  );

  // ---- periodic auto-sync (WorkManager equivalent) ----------------------
  useEffect(() => {
    if (!accountId || !preferences.autoSync || !snapshot) return;
    container.syncManager.startPeriodic(accountId);
    return () => container.syncManager.stopPeriodic();
  }, [accountId, preferences.autoSync, snapshot]);

  // ---- actions ----------------------------------------------------------
  const login = useCallback<PronoteContextValue["login"]>(
    async (params) => {
      setFatalError(null);
      const result = await container.useCases.login.execute(params);

      if (result instanceof Error) {
        const error = result as PronoteError;
        setFatalError({ error: error.userMessage, hint: error.hint });
        return { ok: false, error };
      }

      writeActiveAccount(params.accountId);
      setSession(result);
      container.syncManager.reset();
      setSnapshot(container.repository.getCachedSnapshot(params.accountId));
      setPreferences(container.repository.getPreferences(params.accountId));
      setLastSyncError(null);
      setRestoreAttempted(true);
      return { ok: true };
    },
    [],
  );

  const logout = useCallback(async () => {
    if (accountId) await container.useCases.logout.execute(accountId);
    setSession(null);
    setSnapshot(null);
    setFatalError(null);
  }, [accountId]);

  const deleteLocalData = useCallback(async () => {
    if (accountId) await container.useCases.deleteLocalData.execute(accountId);
    writeActiveAccount(null);
    setSession(null);
    setSnapshot(null);
    setFatalError(null);
    setLastSyncError(null);
  }, [accountId]);

  const updatePreferences = useCallback<PronoteContextValue["updatePreferences"]>(
    (prefs) => {
      const target = accountId ?? GUEST_ACCOUNT;
      container.useCases.preferences.save(target, prefs);
      setPreferences((prev) => ({ ...prev, ...prefs }));
    },
    [accountId],
  );

  const refresh = useCallback(
    async (options?: { force?: boolean }) => {
      await runSync(options ?? { force: true });
    },
    [runSync],
  );

  const value = useMemo<PronoteContextValue>(
    () => ({
      state,
      session,
      snapshot,
      accountId,
      preferences,
      online,
      syncing,
      login,
      logout,
      refresh,
      updatePreferences,
      deleteLocalData,
      lastSyncError,
    }),
    [
      state,
      session,
      snapshot,
      accountId,
      preferences,
      online,
      syncing,
      login,
      logout,
      refresh,
      updatePreferences,
      deleteLocalData,
      lastSyncError,
    ],
  );

  return <PronoteContext.Provider value={value}>{children}</PronoteContext.Provider>;
}

export function usePronote(): PronoteContextValue {
  const context = useContext(PronoteContext);
  if (!context) throw new Error("usePronote must be used inside PronoteProvider");
  return context;
}

/**
 * PronoteRoot — derives the account id from the signed-in user and provides the
 * PRONOTE store. Wrap every authenticated route with it.
 */
export function PronoteRoot({ children }: { children: React.ReactNode }) {
  const { online } = useNetworkMonitor();
  const accountId = useAccountId();
  return (
    <PronoteProvider accountId={accountId} online={online}>
      {children}
    </PronoteProvider>
  );
}

/** Reads the signed-in Convex user id and turns it into a stable local id. */
export function useAccountId(): string | null {
  const { user } = useAuth();
  return user?._id ? `acct-${user._id}` : readActiveAccount();
}
