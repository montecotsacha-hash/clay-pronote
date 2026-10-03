import { describe, expect, it } from "vitest";
import { PronoteRepositoryImpl } from "../src/lib/pronote/repository";
import {
  buildMockPayload,
  buildMockPayloadWithOfficialAverage,
} from "../src/lib/pronote/fake-data-source";
import { PronoteCompatibilityLayer } from "../src/lib/pronote/data-source";
import { SessionManager } from "../src/lib/pronote/session-manager";
import { normalizeGrade } from "../src/lib/pronote/average";

const URL = "https://0123456a.index-education.net/pronote/";

function repo() {
  const connector = new PronoteCompatibilityLayer({
    id: "test",
    strategy: "DirectPronoteStrategy",
    authenticate: async () => {
      throw new Error("unused");
    },
    restoreSession: async () => {
      throw new Error("unused");
    },
    fetchAll: async () => {
      throw new Error("unused");
    },
    logout: async () => {},
  });
  return new PronoteRepositoryImpl(connector, new SessionManager({ connector }));
}

describe("PronoteRepositoryImpl.toSnapshot", () => {
  it("computes a CALCULATED general average when PRONOTE hides it", () => {
    const snapshot = repo().toSnapshot(buildMockPayload(URL));

    expect(snapshot.generalAverage).not.toBeNull();
    expect(snapshot.generalAverage?.source).toBe("CALCULATED");
    expect(snapshot.student.firstName).toBe("Alex");
    expect(snapshot.subjects).toHaveLength(6);
    expect(snapshot.grades.length).toBeGreaterThan(10);
  });

  it("keeps the OFFICIAL average when PRONOTE provides one", () => {
    const snapshot = repo().toSnapshot(buildMockPayloadWithOfficialAverage(URL));

    expect(snapshot.generalAverage).toEqual(
      expect.objectContaining({ value: 15.8, source: "OFFICIAL" }),
    );
  });

  it("maps grade subject ids onto the domain subject ids", () => {
    const snapshot = repo().toSnapshot(buildMockPayload(URL));
    const subjectIds = new Set(snapshot.subjects.map((s) => s.id));
    for (const grade of snapshot.grades) {
      expect(subjectIds.has(grade.subjectId)).toBe(true);
    }
  });

  it("keeps the non significatif grade out of the computed averages", () => {
    const snapshot = repo().toSnapshot(buildMockPayload(URL));
    const ignored = snapshot.grades.filter((g) => !g.countsTowardAverage);
    expect(ignored.length).toBeGreaterThan(0);

    // English official average is 17.2; the 5/20 non-significant grade must not
    // drag the locally computed value down to that level.
    const english = snapshot.subjectAverages.find((a) => a.subjectId === "anglais");
    expect(english?.value).toBe(17.2);

    const counted = snapshot.grades
      .filter((g) => g.subjectId === "anglais" && g.countsTowardAverage)
      .map(normalizeGrade);
    const mean = counted.reduce((sum, v) => sum + v, 0) / counted.length;
    expect(mean).toBeGreaterThan(15);
  });

  it("produces a usable history with the current average appended", () => {
    const snapshot = repo().toSnapshot(buildMockPayload(URL));
    expect(snapshot.history.length).toBeGreaterThan(0);
    const last = snapshot.history[snapshot.history.length - 1];
    expect(last.value).toBe(snapshot.generalAverage?.value);
  });

  it("marks the current period from the payload", () => {
    const snapshot = repo().toSnapshot(buildMockPayload(URL));
    expect(snapshot.periods.find((p) => p.current)?.id).toBe("T2");
  });

  it("survives an account with no grades at all", () => {
    const payload = buildMockPayload(URL);
    payload.grades = [];
    payload.subjectAverages = [];
    payload.generalAverage = null;

    const snapshot = repo().toSnapshot(payload);
    expect(snapshot.generalAverage).toBeNull();
    expect(snapshot.grades).toHaveLength(0);
    expect(snapshot.history).toHaveLength(0);
  });

  it("handles a missing official average gracefully", () => {
    const payload = buildMockPayload(URL);
    payload.generalAverage = null;
    expect(repo().toSnapshot(payload).generalAverage?.source).toBe("CALCULATED");
  });
});
