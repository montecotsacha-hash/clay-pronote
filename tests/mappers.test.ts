import { describe, expect, it } from "vitest";
import {
  mapGrade,
  mapStudent,
  mapSubject,
  mapSubjectAverage,
  toGradeUiModel,
  toPeriodId,
  toSubjectAverageUiModel,
} from "../src/lib/pronote/mappers";

describe("toPeriodId", () => {
  it("maps numeric and textual periods", () => {
    expect(toPeriodId("1")).toBe("T1");
    expect(toPeriodId("t2")).toBe("T2");
    expect(toPeriodId("ANNEE")).toBe("ANNUAL");
  });

  it("falls back when the period is unknown", () => {
    expect(toPeriodId("bidule", "S1")).toBe("S1");
    expect(toPeriodId(undefined, "T3")).toBe("T3");
  });
});

describe("GradeMapper (mapGrade)", () => {
  it("maps a French PRONOTE payload to the domain model", () => {
    const grade = mapGrade({
      id: 42,
      matiere: "maths",
      intitule: "Contrôle",
      note: "15,5",
      bareme: 20,
      coefficient: "2",
      date: "2026-02-10",
      commentaire: "Bien",
    });
    expect(grade).toMatchObject({
      id: "42",
      subjectId: "maths",
      title: "Contrôle",
      value: 15.5,
      maxValue: 20,
      coefficient: 2,
      date: "2026-02-10",
      comment: "Bien",
      countsTowardAverage: true,
    });
  });

  it("marks a non significatif grade as not counted", () => {
    expect(mapGrade({ note: 5, nonSignificatif: true }).countsTowardAverage).toBe(false);
  });

  it("honours noteSur20 for payloads that only expose the normalised value", () => {
    const grade = mapGrade({ noteSur20: 14, bareme: 10 });
    // 14/20 expressed on a /10 scale => 7
    expect(grade.value).toBe(7);
    expect(grade.maxValue).toBe(10);
  });

  it("never produces a zero scale", () => {
    expect(mapGrade({ note: 5, bareme: 0 }).maxValue).toBeGreaterThan(0);
  });
});

describe("AverageMapper (mapSubjectAverage)", () => {
  it("keeps the official flag and normalises the scale", () => {
    const average = mapSubjectAverage({ matiere: "francais", moyenne: "14,8", bareme: 20, periode: "T2" });
    expect(average).toMatchObject({
      subjectId: "francais",
      value: 14.8,
      outOf: 20,
      period: "T2",
      source: "OFFICIAL",
    });
  });
});

describe("mapStudent / mapSubject", () => {
  it("maps student fields with sensible fallbacks", () => {
    const student = mapStudent({ prenom: "Alex", nom: "Martin", classe: "1re", etablissement: "Lycée" });
    expect(student).toMatchObject({
      firstName: "Alex",
      lastName: "Martin",
      className: "1re",
      schoolName: "Lycée",
    });
  });

  it("defaults the first name when the payload is empty", () => {
    expect(mapStudent({}).firstName).toBe("Élève");
  });

  it("defaults the coefficient to 1 and derives a short name", () => {
    const subject = mapSubject({ nom: "Mathématiques" }, 0);
    expect(subject.coefficient).toBe(1);
    expect(subject.shortName).toBe("Mat");
  });
});

describe("UI model mappers", () => {
  it("formats a grade for display in French", () => {
    const grade = mapGrade({ id: "g1", matiere: "maths", note: 17, bareme: 20, coefficient: 2 });
    const ui = toGradeUiModel(grade, mapSubject({ id: "maths", nom: "Mathématiques" }), "hier");
    expect(ui.display).toBe("17/20");
    expect(ui.subjectName).toBe("Mathématiques");
    expect(ui.normalized).toBe(17);
  });

  it("shows an em dash for a subject with no average", () => {
    const ui = toSubjectAverageUiModel(mapSubject({ id: "svt", nom: "SVT" }), null, null, 0);
    expect(ui.display).toBe("—");
    expect(ui.deltaDisplay).toBeNull();
  });

  it("formats a signed delta", () => {
    const ui = toSubjectAverageUiModel(mapSubject({ id: "maths", nom: "Maths" }), 16.4, 0.4, 4);
    expect(ui.deltaDisplay).toBe("+0,4");
  });
});
