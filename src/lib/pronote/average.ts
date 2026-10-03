import type {
  Grade,
  GeneralAverage,
  PeriodId,
  Subject,
  SubjectAverage,
} from "./types";

/**
 * AverageCalculator — pure, dependency-free, fully unit tested.
 *
 * Rules encoded here:
 *  - PRONOTE does not always use a simple mean: coefficients, weights,
 *    "non significatif" grades and per-subject coefficients all matter.
 *  - When PRONOTE already gives an official average we keep it as OFFICIAL and
 *    only compute a local value as a fallback.
 */

const clamp = (value: number, min: number, max: number) =>
  Math.min(max, Math.max(min, value));

/** Normalise a grade onto /20 so mixed scales stay comparable. */
export function normalizeGrade(grade: Grade): number {
  if (grade.maxValue <= 0) return 0;
  return (grade.value / grade.maxValue) * 20;
}

export function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

/**
 * Weighted mean of a grade list.
 * Weight = coefficient (PRONOTE coefficient), defaulting to 1.
 */
export function weightedMean(
  grades: { value: number; coefficient: number }[],
): number | null {
  const usable = grades.filter((g) => Number.isFinite(g.value));
  if (usable.length === 0) return null;

  const totalWeight = usable.reduce(
    (sum, g) => sum + Math.max(g.coefficient, 0.0001),
    0,
  );
  if (totalWeight <= 0) return null;

  const weighted = usable.reduce(
    (sum, g) => sum + g.value * Math.max(g.coefficient, 0.0001),
    0,
  );
  return round2(weighted / totalWeight);
}

/** Grades that actually count toward an average. */
export function significantGrades(grades: Grade[]): Grade[] {
  return grades.filter((g) => g.countsTowardAverage && g.maxValue > 0);
}

/**
 * Average of one subject.
 * Grades carry no period of their own, so the caller scopes the list first.
 */
export function subjectAverage(
  subject: Subject,
  grades: Grade[],
): number | null {
  const scoped = significantGrades(grades).filter(
    (g) => g.subjectId === subject.id,
  );
  if (scoped.length === 0) return null;
  return weightedMean(
    scoped.map((g) => ({
      value: normalizeGrade(g),
      coefficient: g.coefficient,
    })),
  );
}

/**
 * General average.
 *
 * Two-pass strategy:
 *  1. If PRONOTE supplied subject averages we weight them by the subject
 *     coefficient (this is what PRONOTE itself does).
 *  2. Otherwise we fall back to every significant grade, so a student whose
 *     average is hidden in the PRONOTE UI still sees a locally computed value.
 */
export function generalAverageFromSubjects(
  subjects: Subject[],
  averages: SubjectAverage[],
): number | null {
  const bySubject = new Map(averages.map((a) => [a.subjectId, a]));
  const rows = subjects
    .map((subject) => {
      const avg = bySubject.get(subject.id);
      if (!avg) return null;
      return { value: avg.value, coefficient: subject.coefficient || 1 };
    })
    .filter((row): row is { value: number; coefficient: number } => row !== null);

  return weightedMean(rows);
}

export function generalAverageFromGrades(grades: Grade[]): number | null {
  return weightedMean(
    significantGrades(grades).map((g) => ({
      value: normalizeGrade(g),
      coefficient: g.coefficient,
    })),
  );
}

export interface ComputedAverage {
  value: number;
  outOf: number;
  source: "OFFICIAL" | "CALCULATED";
}

export interface CalculateOptions {
  /** Official average already provided by PRONOTE, if any. */
  official: GeneralAverage | null;
  subjects: Subject[];
  grades: Grade[];
  subjectAverages: SubjectAverage[];
  period: PeriodId;
}

export function calculateGeneralAverage({
  official,
  subjects,
  grades,
  subjectAverages,
  period,
}: CalculateOptions): ComputedAverage | null {
  // 1. Trust the official value when PRONOTE gives one.
  if (official && Number.isFinite(official.value)) {
    return { value: round2(official.value), outOf: official.outOf, source: "OFFICIAL" };
  }

  // 2. Rebuild from the subject averages of the requested period.
  const scoped = subjectAverages.filter((a) => a.period === period);
  const fromSubjects = generalAverageFromSubjects(
    subjects,
    scoped.length > 0 ? scoped : subjectAverages,
  );
  if (fromSubjects !== null) {
    return { value: fromSubjects, outOf: 20, source: "CALCULATED" };
  }

  // 3. Last resort: every significant grade.
  const fromGrades = generalAverageFromGrades(grades);
  if (fromGrades !== null) {
    return { value: fromGrades, outOf: 20, source: "CALCULATED" };
  }

  return null;
}

/** Grades of a subject, newest first. */
export function sortGradesDesc(grades: Grade[]): Grade[] {
  return [...grades].sort((a, b) => b.date.localeCompare(a.date));
}

/**
 * Evolution of a subject: latest grade vs. the running average before it.
 * Positive => improving.
 */
export function subjectTrend(
  subject: Subject,
  grades: Grade[],
): number | null {
  const sorted = sortGradesDesc(
    significantGrades(grades).filter((g) => g.subjectId === subject.id),
  );
  if (sorted.length < 2) return null;

  const [latest, ...rest] = sorted;
  const previous = weightedMean(
    rest.map((g) => ({
      value: normalizeGrade(g),
      coefficient: g.coefficient,
    })),
  );
  if (previous === null) return null;

  return round2(normalizeGrade(latest) - previous);
}

export function clamp20(value: number): number {
  return round2(clamp(value, 0, 20));
}

/** Formatting helpers shared by UI models. */
export function formatAverage(value: number): string {
  return value.toFixed(1).replace(".", ",");
}

export function formatDelta(delta: number): string {
  const sign = delta > 0 ? "+" : delta < 0 ? "−" : "";
  return `${sign}${Math.abs(delta).toFixed(1).replace(".", ",")}`;
}

export function formatGrade(value: number, maxValue: number): string {
  return `${value.toFixed(value % 1 === 0 ? 0 : 1).replace(".", ",")}/${maxValue}`;
}
