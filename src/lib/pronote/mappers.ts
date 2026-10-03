import { round2, normalizeGrade, formatAverage, formatDelta, formatGrade } from "./average";
import type {
  Grade,
  PeriodId,
  Subject,
  SubjectAverage,
} from "./types";

/**
 * Remote DTOs — the wire shape of a PRONOTE connector.
 * These never leave the data layer: `GradeMapper` / `AverageMapper` translate
 * them into domain models.
 *
 * Field names mirror the French PRONOTE payload so a real connector can drop in
 * behind `PronoteDataSource` without touching the rest of the app.
 */

export interface PronoteStudentDto {
  id?: string | number;
  prenom?: string;
  nom?: string;
  classe?: string;
  etablissement?: string;
}

export interface PronoteSubjectDto {
  id?: string | number;
  nom?: string;
  nomCourt?: string;
  professeur?: string;
  coefficient?: number | string;
  couleur?: string;
}

export interface PronoteGradeDto {
  id?: string | number;
  matiere?: string | number;
  intitule?: string;
  note?: number | string;
  bareme?: number | string;
  coefficient?: number | string;
  date?: string;
  commentaire?: string;
  /** PRONOTE marks some grades as "non significatif". */
  nonSignificatif?: boolean;
  /** Some payloads expose a "note / 20" directly. */
  noteSur20?: number | string;
}

export interface PronoteSubjectAverageDto {
  matiere?: string | number;
  moyenne?: number | string;
  bareme?: number | string;
  periode?: string;
}

export interface PronoteGeneralAverageDto {
  moyenne?: number | string;
  bareme?: number | string;
  periode?: string;
}

const toNumber = (value: unknown, fallback = 0): number => {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Number.parseFloat(value.replace(",", "."));
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
};

const asId = (value: unknown, fallback: string): string => {
  if (value === undefined || value === null) return fallback;
  return String(value);
};

const PERIOD_MAP: Record<string, PeriodId> = {
  T1: "T1",
  T2: "T2",
  T3: "T3",
  "1": "T1",
  "2": "T2",
  "3": "T3",
  S1: "S1",
  S2: "S2",
  ANNEE: "ANNUAL",
  ANNUAL: "ANNUAL",
};

export function toPeriodId(value: unknown, fallback: PeriodId = "T1"): PeriodId {
  if (typeof value !== "string") return fallback;
  return PERIOD_MAP[value.trim().toUpperCase()] ?? fallback;
}

/** PronoteStudentDto -> Student */
export function mapStudent(dto: PronoteStudentDto): {
  id: string;
  firstName: string;
  lastName: string;
  className: string;
  schoolName: string;
} {
  return {
    id: asId(dto.id, "student"),
    firstName: (dto.prenom ?? "").trim() || "Élève",
    lastName: (dto.nom ?? "").trim(),
    className: (dto.classe ?? "").trim(),
    schoolName: (dto.etablissement ?? "").trim(),
  };
}

/** PronoteSubjectDto -> Subject */
export function mapSubject(dto: PronoteSubjectDto, index = 0): Subject {
  const name = (dto.nom ?? "Matière").trim();
  return {
    id: asId(dto.id, `subject-${index}`),
    name,
    shortName: (dto.nomCourt ?? name.slice(0, 3)).trim(),
    teacher: (dto.professeur ?? "").trim(),
    coefficient: Math.max(toNumber(dto.coefficient, 1), 0.0001),
    color: dto.couleur ?? "var(--chart-1)",
  };
}

/** PronoteGradeDto -> Grade (domain) */
export function mapGrade(dto: PronoteGradeDto, index = 0): Grade {
  const maxValue = Math.max(toNumber(dto.bareme, 20), 0.0001);
  const rawValue = dto.noteSur20 !== undefined ? toNumber(dto.noteSur20) : toNumber(dto.note);
  const value = dto.noteSur20 !== undefined ? (rawValue / 20) * maxValue : rawValue;

  return {
    id: asId(dto.id, `grade-${index}`),
    subjectId: asId(dto.matiere, "unknown"),
    title: (dto.intitule ?? "Évaluation").trim(),
    value,
    maxValue,
    coefficient: Math.max(toNumber(dto.coefficient, 1), 0.0001),
    date: dto.date ?? new Date().toISOString().slice(0, 10),
    comment: dto.commentaire?.trim() || undefined,
    countsTowardAverage: !dto.nonSignificatif,
    updatedAt: Date.now(),
  };
}

/** PronoteSubjectAverageDto -> SubjectAverage */
export function mapSubjectAverage(dto: PronoteSubjectAverageDto): SubjectAverage {
  const outOf = Math.max(toNumber(dto.bareme, 20), 0.0001);
  return {
    subjectId: asId(dto.matiere, "unknown"),
    value: round2(toNumber(dto.moyenne)),
    outOf,
    period: toPeriodId(dto.periode),
    source: "OFFICIAL",
    updatedAt: Date.now(),
  };
}

/** Grade -> GradeEntity (local persistence shape, Room-equivalent) */
export function gradeToEntity(grade: Grade, accountId: string) {
  return { ...grade, accountId };
}

export type GradeEntity = Grade & { accountId: string };

/** GradeEntity -> Grade (drops the local account scoping) */
export function entityToGrade(entity: GradeEntity): Grade {
  const { accountId, ...grade } = entity;
  void accountId;
  return grade;
}

export interface GradeUiModel extends Grade {
  display: string;
  normalized: number;
  subjectName: string;
  subjectColor: string;
  relativeDate: string;
}

export type DomainGrade = Grade;

/** Grade -> GradeUiModel (the only shape the UI is allowed to render) */
export function toGradeUiModel(
  grade: Grade,
  subject: Subject | undefined,
  relativeDate: string,
): GradeUiModel {
  return {
    ...grade,
    display: formatGrade(grade.value, grade.maxValue),
    normalized: round2(normalizeGrade(grade)),
    subjectName: subject?.name ?? "Matière",
    subjectColor: subject?.color ?? "var(--chart-1)",
    relativeDate,
  };
}

export interface SubjectAverageUiModel {
  subject: Subject;
  value: number;
  display: string;
  delta: number | null;
  deltaDisplay: string | null;
  gradeCount: number;
  source: "OFFICIAL" | "CALCULATED";
}

export function toSubjectAverageUiModel(
  subject: Subject,
  average: number | null,
  delta: number | null,
  gradeCount: number,
  source: "OFFICIAL" | "CALCULATED" = "CALCULATED",
): SubjectAverageUiModel {
  return {
    subject,
    value: average ?? 0,
    display: average === null ? "—" : formatAverage(average),
    delta,
    deltaDisplay: delta === null ? null : formatDelta(delta),
    gradeCount,
    source,
  };
}

