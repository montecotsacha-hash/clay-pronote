/**
 * Domain models — completely independent from the PRONOTE connector.
 *
 * Layer separation (see architecture section of the spec):
 *   PronoteGradeDto -> Grade -> GradeEntity -> GradeUiModel
 * The UI must never touch a DTO or a storage entity directly.
 */

export type PeriodId = "T1" | "T2" | "T3" | "S1" | "S2" | "ANNUAL";

export interface Student {
  id: string;
  firstName: string;
  lastName: string;
  className: string;
  schoolName: string;
}

export interface Subject {
  id: string;
  name: string;
  shortName: string;
  teacher: string;
  coefficient: number;
  /** PRONOTE colour hint, used for the clay accents. */
  color: string;
}

export interface Grade {
  id: string;
  subjectId: string;
  title: string;
  /** Value on the 0..20 scale. */
  value: number;
  maxValue: number;
  /** Coefficient / weight used by the subject average. */
  coefficient: number;
  /** ISO date (YYYY-MM-DD). */
  date: string;
  comment?: string;
  /** false for grades marked "non significatif" by the teacher. */
  countsTowardAverage: boolean;
  updatedAt: number;
}

/** Where an average number came from — never blur the two. */
export type AverageSource = "OFFICIAL" | "CALCULATED";

export interface SubjectAverage {
  subjectId: string;
  value: number;
  /** Normalised on 20. */
  outOf: number;
  period: PeriodId;
  source: AverageSource;
  updatedAt: number;
}

export interface GeneralAverage {
  value: number;
  outOf: number;
  period: PeriodId;
  source: AverageSource;
  /** Signed delta vs. the previous known value, when available. */
  delta?: number;
  updatedAt: number;
}

export interface HistoryPoint {
  /** ISO date. */
  date: string;
  value: number;
  period: PeriodId;
  source: AverageSource;
}

export interface PronoteSession {
  /** Opaque session token — never rendered, never logged. */
  token: string;
  /** Epoch ms. */
  expiresAt: number;
  schoolUrl: string;
  schoolName: string;
  lastSyncAt: number;
  connected: boolean;
}

export interface SyncMetadata {
  lastSync: number | null;
  syncStatus: SyncStatus;
  lastError: string | null;
}

export type SyncStatus =
  | "idle"
  | "syncing"
  | "success"
  | "offline"
  | "error";

/** School snapshot stored locally (Room-equivalent payload). */
export interface SchoolSnapshot {
  student: Student;
  subjects: Subject[];
  grades: Grade[];
  subjectAverages: SubjectAverage[];
  generalAverage: GeneralAverage | null;
  history: HistoryPoint[];
  periods: { id: PeriodId; label: string; current: boolean }[];
  fetchedAt: number;
}

/** App-level explicit state machine. */
export type DataState =
  | { status: "loading" }
  | { status: "refreshing" }
  | { status: "success" }
  | { status: "offline" }
  | { status: "error"; error: string }
  | { status: "unauthenticated" };

export interface AccountProfile {
  /** Stable local account id (derived from the signed-in user). */
  id: string;
  label: string;
  schoolUrl: string;
  schoolName: string;
  username: string;
  /** true when only the session token is kept (password not stored). */
  tokenOnly: boolean;
}
