import {
  formatAverage,
  formatDelta,
  sortGradesDesc,
  subjectAverage,
  subjectTrend,
} from "./average";
import type { PronoteError } from "./errors";
import type { LocalPreferences } from "./local-data-source";
import type { PronoteRepository } from "./repository";
import type {
  GeneralAverage,
  Grade,
  SchoolSnapshot,
  Subject,
  SubjectAverage,
} from "./types";

/**
 * Use cases — the only business entry point for ViewModels.
 * ViewModels must never talk to the connector, the vault or localStorage.
 */

export class LoginUseCase {
  private readonly repository: PronoteRepository;
  constructor(repository: PronoteRepository) {
    this.repository = repository;
  }
  execute(params: {
    accountId: string;
    schoolUrl: string;
    username: string;
    password: string;
  }) {
    return this.repository.login(params);
  }
}

export class LogoutUseCase {
  private readonly repository: PronoteRepository;
  constructor(repository: PronoteRepository) {
    this.repository = repository;
  }
  execute(accountId: string) {
    return this.repository.logout(accountId);
  }
}

export class GetCachedDataUseCase {
  private readonly repository: PronoteRepository;
  constructor(repository: PronoteRepository) {
    this.repository = repository;
  }
  execute(accountId: string): SchoolSnapshot | null {
    return this.repository.getCachedSnapshot(accountId);
  }
}

export class SyncPronoteUseCase {
  private readonly repository: PronoteRepository;
  constructor(repository: PronoteRepository) {
    this.repository = repository;
  }
  execute(accountId: string) {
    return this.repository.sync(accountId);
  }
}

export class RefreshSessionUseCase {
  private readonly repository: PronoteRepository;
  constructor(repository: PronoteRepository) {
    this.repository = repository;
  }
  execute(accountId: string) {
    return this.repository.refreshSession(accountId);
  }
}

export class DeleteLocalDataUseCase {
  private readonly repository: PronoteRepository;
  constructor(repository: PronoteRepository) {
    this.repository = repository;
  }
  execute(accountId: string) {
    return this.repository.deleteLocalData(accountId);
  }
}

export class PreferencesUseCase {
  private readonly repository: PronoteRepository;
  constructor(repository: PronoteRepository) {
    this.repository = repository;
  }
  load(accountId: string): LocalPreferences {
    return this.repository.getPreferences(accountId);
  }
  save(accountId: string, prefs: Partial<LocalPreferences>): void {
    this.repository.savePreferences(accountId, prefs);
  }
}

export class GetStudentUseCase {
  execute(snapshot: SchoolSnapshot | null) {
    return snapshot?.student ?? null;
  }
}

export class GetSubjectsUseCase {
  execute(snapshot: SchoolSnapshot | null): Subject[] {
    return snapshot?.subjects ?? [];
  }
}

export class GetGradesUseCase {
  execute(snapshot: SchoolSnapshot | null, subjectId?: string): Grade[] {
    if (!snapshot) return [];
    const grades = subjectId
      ? snapshot.grades.filter((g) => g.subjectId === subjectId)
      : snapshot.grades;
    return sortGradesDesc(grades);
  }
}

export class GetAveragesUseCase {
  execute(snapshot: SchoolSnapshot | null): SubjectAverage[] {
    return snapshot?.subjectAverages ?? [];
  }
}

export interface AverageSummary {
  value: number;
  outOf: number;
  source: "OFFICIAL" | "CALCULATED";
  display: string;
  delta: number | null;
  deltaDisplay: string | null;
  updatedAt: number;
}

export class CalculateGeneralAverageUseCase {
  execute(snapshot: SchoolSnapshot | null): AverageSummary | null {
    const average: GeneralAverage | undefined = snapshot?.generalAverage ?? undefined;
    if (!snapshot || !average) return null;

    const delta = snapshot.history.length > 1
      ? Math.round((average.value - snapshot.history[snapshot.history.length - 2].value) * 100) / 100
      : null;

    return {
      value: average.value,
      outOf: average.outOf,
      source: average.source,
      display: formatAverage(average.value),
      delta,
      deltaDisplay: delta === null ? null : formatDelta(delta),
      updatedAt: average.updatedAt,
    };
  }
}

export interface SubjectRow {
  subject: Subject;
  average: number | null;
  display: string;
  delta: number | null;
  deltaDisplay: string | null;
  gradeCount: number;
  source: "OFFICIAL" | "CALCULATED";
}

export class GetSubjectRowsUseCase {
  execute(snapshot: SchoolSnapshot | null): SubjectRow[] {
    if (!snapshot) return [];
    const bySubject = new Map(snapshot.subjectAverages.map((a) => [a.subjectId, a]));

    return snapshot.subjects.map((subject) => {
      const official = bySubject.get(subject.id);
      const computed = subjectAverage(subject, snapshot.grades);
      const average = official?.value ?? computed;
      const delta = subjectTrend(subject, snapshot.grades);
      return {
        subject,
        average,
        display: average === null ? "—" : formatAverage(average),
        delta,
        deltaDisplay: delta === null ? null : formatDelta(delta),
        gradeCount: snapshot.grades.filter((g) => g.subjectId === subject.id).length,
        source: official ? official.source : "CALCULATED",
      };
    });
  }
}

/** Signed error helper so the UI can distinguish typed failures. */
export function isPronoteError(value: unknown): value is PronoteError {
  return value instanceof Error && value.name === "PronoteError";
}
