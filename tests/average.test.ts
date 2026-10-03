import { describe, expect, it } from "vitest";
import {
  calculateGeneralAverage,
  generalAverageFromGrades,
  generalAverageFromSubjects,
  normalizeGrade,
  subjectAverage,
  subjectTrend,
  weightedMean,
} from "../src/lib/pronote/average";
import type { Grade, Subject, SubjectAverage } from "../src/lib/pronote/types";

const subject = (id: string, coefficient = 1): Subject => ({
  id,
  name: `Matière ${id}`,
  shortName: id.toUpperCase(),
  teacher: "M.",
  coefficient,
  color: "var(--chart-1)",
});

const grade = (over: Partial<Grade> & { subjectId: string; value: number }): Grade => ({
  id: over.id ?? `g-${Math.random()}`,
  subjectId: over.subjectId,
  title: over.title ?? "Évaluation",
  value: over.value,
  maxValue: over.maxValue ?? 20,
  coefficient: over.coefficient ?? 1,
  date: over.date ?? "2026-01-10",
  countsTowardAverage: over.countsTowardAverage ?? true,
  updatedAt: Date.now(),
});

describe("normalizeGrade", () => {
  it("normalises a /10 grade onto /20", () => {
    expect(normalizeGrade(grade({ subjectId: "m", value: 8, maxValue: 10 }))).toBe(16);
  });

  it("returns 0 for a non-positive scale", () => {
    expect(normalizeGrade(grade({ subjectId: "m", value: 5, maxValue: 0 }))).toBe(0);
  });
});

describe("weightedMean", () => {
  it("applies coefficients — the spec example (15 coef 2, 12 coef 1 => 14)", () => {
    expect(
      weightedMean([
        { value: 15, coefficient: 2 },
        { value: 12, coefficient: 1 },
      ]),
    ).toBe(14);
  });

  it("is a plain mean when every coefficient is 1", () => {
    expect(
      weightedMean([
        { value: 10, coefficient: 1 },
        { value: 20, coefficient: 1 },
      ]),
    ).toBe(15);
  });

  it("returns null when there is nothing to average", () => {
    expect(weightedMean([])).toBeNull();
  });
});

describe("subjectAverage", () => {
  it("ignores grades marked non significatif", () => {
    const grades = [
      grade({ subjectId: "maths", value: 10 }),
      grade({ subjectId: "maths", value: 20, countsTowardAverage: false }),
    ];
    expect(subjectAverage(subject("maths"), grades)).toBe(10);
  });

  it("only counts the subject's own grades", () => {
    const grades = [
      grade({ subjectId: "maths", value: 12 }),
      grade({ subjectId: "francais", value: 18 }),
    ];
    expect(subjectAverage(subject("maths"), grades)).toBe(12);
  });

  it("returns null for a subject with no grades", () => {
    expect(subjectAverage(subject("svt"), [])).toBeNull();
  });
});

describe("generalAverageFromSubjects", () => {
  it("weights subject averages by subject coefficient", () => {
    const subjects = [subject("maths", 6), subject("francais", 4)];
    const averages: SubjectAverage[] = [
      { subjectId: "maths", value: 16, outOf: 20, period: "T2", source: "OFFICIAL", updatedAt: 0 },
      { subjectId: "francais", value: 11, outOf: 20, period: "T2", source: "OFFICIAL", updatedAt: 0 },
    ];
    // (16*6 + 11*4) / 10 = 14
    expect(generalAverageFromSubjects(subjects, averages)).toBe(14);
  });

  it("returns null when no subject average is known", () => {
    expect(generalAverageFromSubjects([subject("maths")], [])).toBeNull();
  });
});

describe("calculateGeneralAverage", () => {
  const subjects = [subject("maths", 2), subject("francais", 1)];
  const grades = [
    grade({ subjectId: "maths", value: 16 }),
    grade({ subjectId: "francais", value: 10 }),
  ];

  it("prefers the OFFICIAL value from PRONOTE and does not recompute", () => {
    const result = calculateGeneralAverage({
      official: {
        value: 15.8,
        outOf: 20,
        period: "T2",
        source: "OFFICIAL",
        updatedAt: 0,
      },
      subjects,
      grades,
      subjectAverages: [],
      period: "T2",
    });
    expect(result).toEqual({ value: 15.8, outOf: 20, source: "OFFICIAL" });
  });

  it("calculates locally when PRONOTE hides the average", () => {
    const subjectAverages: SubjectAverage[] = [
      { subjectId: "maths", value: 16, outOf: 20, period: "T2", source: "OFFICIAL", updatedAt: 0 },
      { subjectId: "francais", value: 10, outOf: 20, period: "T2", source: "OFFICIAL", updatedAt: 0 },
    ];
    const result = calculateGeneralAverage({
      official: null,
      subjects,
      grades,
      subjectAverages,
      period: "T2",
    });
    // (16*2 + 10*1) / 3 = 14
    expect(result).toEqual({ value: 14, outOf: 20, source: "CALCULATED" });
  });

  it("falls back to raw grades (weighted by grade coefficient) when no subject average exists", () => {
    const result = calculateGeneralAverage({
      official: null,
      subjects,
      grades,
      subjectAverages: [],
      period: "T2",
    });
    expect(result?.source).toBe("CALCULATED");
    // Both grades carry coefficient 1 => (16 + 10) / 2 = 13
    expect(result?.value).toBe(13);
  });

  it("returns null for an account with no grades at all", () => {
    expect(
      calculateGeneralAverage({
        official: null,
        subjects: [],
        grades: [],
        subjectAverages: [],
        period: "T2",
      }),
    ).toBeNull();
  });
});

describe("generalAverageFromGrades", () => {
  it("handles mixed scales via normalisation", () => {
    const grades = [
      grade({ subjectId: "maths", value: 8, maxValue: 10, coefficient: 1 }), // 16/20
      grade({ subjectId: "maths", value: 12, maxValue: 20, coefficient: 1 }), // 12/20
    ];
    expect(generalAverageFromGrades(grades)).toBe(14);
  });
});

describe("subjectTrend", () => {
  it("compares the latest grade against the running average", () => {
    const grades = [
      grade({ subjectId: "maths", value: 12, date: "2026-01-01" }),
      grade({ subjectId: "maths", value: 14, date: "2026-02-01" }),
      grade({ subjectId: "maths", value: 18, date: "2026-03-01" }),
    ];
    // latest 18 vs mean(12,14)=13 => +5
    expect(subjectTrend(subject("maths"), grades)).toBe(5);
  });

  it("returns null with fewer than two grades", () => {
    expect(
      subjectTrend(subject("maths"), [grade({ subjectId: "maths", value: 12 })]),
    ).toBeNull();
  });
});
