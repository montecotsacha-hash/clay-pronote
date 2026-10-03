import { calculateGeneralAverage, subjectAverage } from "./average";
import {
  PronoteCompatibilityLayer,
  type PronoteRawPayload,
} from "./data-source";
import { toPronoteError, type PronoteError } from "./errors";
import { LocalDataSource, type LocalPreferences } from "./local-data-source";
import { logger } from "./logger";
import {
  mapGrade,
  mapStudent,
  mapSubject,
  mapSubjectAverage,
  toPeriodId,
} from "./mappers";
import { SessionManager } from "./session-manager";
import type {
  GeneralAverage,
  Grade,
  HistoryPoint,
  PeriodId,
  PronoteSession,
  SchoolSnapshot,
  SubjectAverage,
} from "./types";

/**
 * `PronoteRepository` — the abstraction the rest of the app depends on.
 */
export interface PronoteRepository {
  login(params: {
    accountId: string;
    schoolUrl: string;
    username: string;
    password: string;
  }): Promise<PronoteSession | PronoteError>;
  refreshSession(accountId: string): Promise<PronoteSession | null>;
  getCachedSnapshot(accountId: string): SchoolSnapshot | null;
  sync(accountId: string): Promise<SchoolSnapshot | PronoteError>;
  getPreferences(accountId: string): LocalPreferences;
  savePreferences(accountId: string, prefs: Partial<LocalPreferences>): void;
  logout(accountId: string): Promise<void>;
  deleteLocalData(accountId: string): Promise<void>;
}

export class PronoteRepositoryImpl implements PronoteRepository {
  private readonly connector: PronoteCompatibilityLayer;
  private readonly sessions: SessionManager;
  private readonly local: typeof LocalDataSource;

  constructor(
    connector: PronoteCompatibilityLayer,
    sessions: SessionManager,
    local: typeof LocalDataSource = LocalDataSource,
  ) {
    this.connector = connector;
    this.sessions = sessions;
    this.local = local;
  }

  /** Cache-first: the caller renders this immediately, before any network. */
  getCachedSnapshot(accountId: string): SchoolSnapshot | null {
    return this.local.loadSnapshot(accountId);
  }

  getPreferences(accountId: string): LocalPreferences {
    return this.local.loadPreferences(accountId);
  }

  savePreferences(accountId: string, prefs: Partial<LocalPreferences>): void {
    this.local.savePreferences(accountId, prefs);
  }

  /**
   * Full login. Stores the session token (never the password when a token is
   * available) in the encrypted vault, then persists the school snapshot.
   */
  async login({
    accountId,
    schoolUrl,
    username,
    password,
  }: {
    accountId: string;
    schoolUrl: string;
    username: string;
    password: string;
  }): Promise<PronoteSession | PronoteError> {
    try {
      const result = await this.connector.authenticate(schoolUrl, username, password);
      const session: PronoteSession = {
        token: result.token,
        expiresAt: result.expiresAt,
        schoolUrl,
        schoolName: result.schoolName,
        lastSyncAt: Date.now(),
        connected: true,
      };

      // Prefer the token; keep the password only if the connector gave no token.
      await this.sessions.save(accountId, {
        schoolUrl,
        schoolName: result.schoolName,
        username,
        token: result.token,
        expiresAt: result.expiresAt,
        ...(result.token ? {} : { password }),
      });

      this.local.saveSnapshot(accountId, this.toSnapshot(result.payload));
      return session;
    } catch (error) {
      return error as PronoteError;
    }
  }

  async refreshSession(accountId: string): Promise<PronoteSession | null> {
    return this.sessions.refreshSession(accountId);
  }

  /** Fetch fresh data and replace the local snapshot. */
  async sync(accountId: string): Promise<SchoolSnapshot | PronoteError> {
    const stored = await this.sessions.load(accountId);
    if (!stored) return toPronoteError("SessionExpired");

    try {
      const token = stored.token ?? "";
      const payload = await this.connector.fetchAll(token, stored.schoolUrl);
      const snapshot = this.toSnapshot(payload);
      this.local.saveSnapshot(accountId, snapshot);
      logger.info("Grades updated");
      return snapshot;
    } catch (error) {
      return error as PronoteError;
    }
  }

  async logout(accountId: string): Promise<void> {
    const stored = await this.sessions.load(accountId);
    if (stored?.token) await this.connector.logout(stored.token);
    await this.sessions.clearSession(accountId);
  }

  /** "Supprimer mes données" — snapshot, preferences and secrets. */
  async deleteLocalData(accountId: string): Promise<void> {
    this.local.clearAccount(accountId);
    await this.sessions.clearSession(accountId);
  }

  /**
   * Remote payload -> local snapshot.
   *
   * The general average is OFFICIAL when PRONOTE provides one, otherwise it is
   * CALCULATED locally so a hidden average still shows up.
   */
  toSnapshot(payload: PronoteRawPayload): SchoolSnapshot {
    const student = mapStudent(payload.student);
    const subjects = payload.subjects.map((s, i) => mapSubject(s, i));

    // Grade DTOs reference subjects by their connector id, so build the map from
    // the raw subject ids to the mapped domain ids first.
    const idMap = new Map<string, string>();
    payload.subjects.forEach((dto, i) => {
      idMap.set(String(dto.id ?? `subject-${i}`), subjects[i].id);
    });

    const grades: Grade[] = payload.grades.map((dto, i) => {
      const grade = mapGrade(dto, i);
      const mapped = idMap.get(String(dto.matiere ?? ""));
      return { ...grade, subjectId: mapped ?? grade.subjectId };
    });

    const subjectAverages: SubjectAverage[] = payload.subjectAverages.map((dto) => {
      const avg = mapSubjectAverage(dto);
      const mapped = idMap.get(String(dto.matiere ?? ""));
      return { ...avg, subjectId: mapped ?? avg.subjectId };
    });

    const periods = payload.periods.map((p) => ({
      id: toPeriodId(p.id),
      label: p.label,
      current: p.current,
    }));
    const currentPeriod: PeriodId =
      periods.find((p) => p.current)?.id ?? periods[0]?.id ?? "T1";

    const official: GeneralAverage | null = payload.generalAverage
      ? {
          value: Number.parseFloat(
            String(payload.generalAverage.moyenne).replace(",", "."),
          ),
          outOf: Number.parseFloat(
            String(payload.generalAverage.bareme ?? 20).replace(",", "."),
          ) || 20,
          period: toPeriodId(payload.generalAverage.periode, currentPeriod),
          source: "OFFICIAL",
          updatedAt: Date.now(),
        }
      : null;

    const computed = calculateGeneralAverage({
      official,
      subjects,
      grades,
      subjectAverages,
      period: currentPeriod,
    });

    const generalAverage: GeneralAverage | null = computed
      ? {
          value: computed.value,
          outOf: computed.outOf,
          period: currentPeriod,
          source: computed.source,
          updatedAt: Date.now(),
        }
      : null;

    return {
      student,
      subjects,
      grades,
      subjectAverages: subjects.map((subject) => {
        const remote = subjectAverages.find((a) => a.subjectId === subject.id);
        const computedLocally = subjectAverage(subject, grades);
        return {
          subjectId: subject.id,
          value: remote?.value ?? computedLocally ?? 0,
          outOf: remote?.outOf ?? 20,
          period: remote?.period ?? currentPeriod,
          source: remote ? "OFFICIAL" : "CALCULATED",
          updatedAt: Date.now(),
        };
      }),
      generalAverage,
      history: buildHistory(grades, generalAverage, currentPeriod),
      periods: periods.length
        ? periods
        : [{ id: currentPeriod, label: "Période en cours", current: true }],
      fetchedAt: Date.now(),
    };
  }
}

/** A compact, deterministic history built from the grades we know about. */
export function buildHistory(
  grades: Grade[],
  current: GeneralAverage | null,
  period: PeriodId,
): HistoryPoint[] {
  const sorted = [...grades].sort((a, b) => a.date.localeCompare(b.date));
  if (sorted.length === 0) return [];

  const points: HistoryPoint[] = [];
  const running: Grade[] = [];
  let lastBucket = "";

  for (const grade of sorted) {
    running.push(grade);
    const bucket = grade.date.slice(0, 7); // month
    const value = runningAverage(running);
    if (bucket !== lastBucket && value !== null) {
      points.push({
        date: grade.date,
        value,
        period,
        source: current?.source ?? "CALCULATED",
      });
      lastBucket = bucket;
    }
  }

  if (current && points.length > 0) {
    points.push({
      date: new Date().toISOString().slice(0, 10),
      value: current.value,
      period,
      source: current.source,
    });
  }

  return points;
}

function runningAverage(grades: Grade[]): number | null {
  const significant = grades.filter((g) => g.countsTowardAverage && g.maxValue > 0);
  if (significant.length === 0) return null;
  const total = significant.reduce(
    (sum, g) => sum + (g.value / g.maxValue) * 20 * g.coefficient,
    0,
  );
  const weights = significant.reduce((sum, g) => sum + g.coefficient, 0);
  return weights > 0 ? Math.round((total / weights) * 100) / 100 : null;
}
