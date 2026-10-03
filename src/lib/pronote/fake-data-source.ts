import type { PronoteAuthResult, PronoteDataSource, PronoteRawPayload } from "./data-source";
import { toPronoteError } from "./errors";
import { logger } from "./logger";

/**
 * FakePronoteRepository (connector level).
 *
 * Lets the whole app be developed and demoed without a live PRONOTE account.
 * It mimics the real connector's failure modes so the error UI can be tested:
 *   - wrong password            -> InvalidCredentials
 *   - unreachable host          -> PronoteUnavailable
 *   - "captcha" in the URL      -> CaptchaRequired
 *   - offline                   -> NetworkUnavailable
 */

export interface MockOptions {
  latency?: number;
  /** Force a deterministic grade set so the average card is stable. */
  seed?: number;
}

const PERIODS = [
  { id: "T1", label: "1er trimestre", current: false },
  { id: "T2", label: "2e trimestre", current: true },
  { id: "T3", label: "3e trimestre", current: false },
];

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

/** The mock school data — deliberately close to the spec's example. */
export function buildMockPayload(schoolUrl: string): PronoteRawPayload {
  const schoolName =
    schoolUrl.includes("index-education")
      ? "Lycée Jean Moulin"
      : "Établissement de démonstration";

  return {
    student: {
      id: "student-1",
      prenom: "Alex",
      nom: "Martin",
      classe: "1re Générale — G2",
      etablissement: schoolName,
    },
    subjects: [
      { id: "maths", nom: "Mathématiques", nomCourt: "MAT", professeur: "Mme Dubois", coefficient: 6, couleur: "var(--chart-1)" },
      { id: "francais", nom: "Français", nomCourt: "FRA", professeur: "M. Leroy", coefficient: 4, couleur: "var(--chart-4)" },
      { id: "anglais", nom: "Anglais", nomCourt: "ANG", professeur: "Ms. Carter", coefficient: 3, couleur: "var(--chart-2)" },
      { id: "physique", nom: "Physique-Chimie", nomCourt: "PHC", professeur: "M. Bernard", coefficient: 5, couleur: "var(--chart-5)" },
      { id: "histoire", nom: "Histoire-Géo", nomCourt: "HGE", professeur: "Mme Petit", coefficient: 3, couleur: "var(--chart-3)" },
      { id: "ses", nom: "SES", nomCourt: "SES", professeur: "M. Roux", coefficient: 2, couleur: "var(--chart-2)" },
    ],
    grades: [
      { id: "g1", matiere: "maths", intitule: "Contrôle fonctions", note: 17, bareme: 20, coefficient: 2, date: isoDaysAgo(1), commentaire: "Très bon travail." },
      { id: "g2", matiere: "maths", intitule: "DM — Suites", note: 15, bareme: 20, coefficient: 1, date: isoDaysAgo(6) },
      { id: "g3", matiere: "maths", intitule: "Interrogation dérivées", note: 16, bareme: 20, coefficient: 1, date: isoDaysAgo(13) },
      { id: "g4", matiere: "maths", intitule: "Évaluation probabilités", note: 8, bareme: 10, coefficient: 1, date: isoDaysAgo(21) },
      { id: "g5", matiere: "francais", intitule: "Commentaire composé", note: 14, bareme: 20, coefficient: 2, date: isoDaysAgo(2) },
      { id: "g6", matiere: "francais", intitule: "Dissertation", note: 15, bareme: 20, coefficient: 2, date: isoDaysAgo(18) },
      { id: "g7", matiere: "francais", intitule: "Oral", note: 15.5, bareme: 20, coefficient: 1, date: isoDaysAgo(30) },
      { id: "g8", matiere: "anglais", intitule: "Compréhension orale", note: 18, bareme: 20, coefficient: 1, date: isoDaysAgo(3) },
      { id: "g9", matiere: "anglais", intitule: "Expression écrite", note: 16, bareme: 20, coefficient: 2, date: isoDaysAgo(15) },
      { id: "g10", matiere: "anglais", intitule: "Thème", note: 17, bareme: 20, coefficient: 1, date: isoDaysAgo(24) },
      { id: "g11", matiere: "physique", intitule: "TP dosage", note: 16, bareme: 20, coefficient: 1, date: isoDaysAgo(4) },
      { id: "g12", matiere: "physique", intitule: "Contrôle ondes", note: 15, bareme: 20, coefficient: 3, date: isoDaysAgo(11) },
      { id: "g13", matiere: "physique", intitule: "Interrogation énergie", note: 8.5, bareme: 10, coefficient: 1, date: isoDaysAgo(26) },
      { id: "g14", matiere: "histoire", intitule: "Composition", note: 13, bareme: 20, coefficient: 2, date: isoDaysAgo(8) },
      { id: "g15", matiere: "histoire", intitule: "Carte mentale", note: 15, bareme: 20, coefficient: 1, date: isoDaysAgo(19) },
      { id: "g16", matiere: "ses", intitule: "Exposé marché", note: 17, bareme: 20, coefficient: 1, date: isoDaysAgo(9) },
      { id: "g17", matiere: "ses", intitule: "Devoir maison", note: 12, bareme: 20, coefficient: 1, date: isoDaysAgo(22) },
      // "non significatif" grade — must not be counted anywhere.
      { id: "g18", matiere: "anglais", intitule: "Test de positionnement", note: 5, bareme: 20, coefficient: 1, date: isoDaysAgo(40), nonSignificatif: true },
    ],
    subjectAverages: [
      { matiere: "maths", moyenne: 16.4, bareme: 20, periode: "T2" },
      { matiere: "francais", moyenne: 14.8, bareme: 20, periode: "T2" },
      { matiere: "anglais", moyenne: 17.2, bareme: 20, periode: "T2" },
      { matiere: "physique", moyenne: 15.6, bareme: 20, periode: "T2" },
      { matiere: "histoire", moyenne: 13.7, bareme: 20, periode: "T2" },
      { matiere: "ses", moyenne: 15.1, bareme: 20, periode: "T2" },
    ],
    // Demo mode: PRONOTE returns no official general average, so the app has to
    // compute it locally — exactly the "hidden average" scenario from the spec.
    generalAverage: null,
    periods: PERIODS,
  };
}

/** Demo grade list with the official average present, to exercise the OFFICIAL path. */
export function buildMockPayloadWithOfficialAverage(
  schoolUrl: string,
): PronoteRawPayload {
  const payload = buildMockPayload(schoolUrl);
  return {
    ...payload,
    generalAverage: { moyenne: 15.8, bareme: 20, periode: "T2" },
  };
}

function assertReachable() {
  if (typeof navigator !== "undefined" && navigator.onLine === false) {
    throw toPronoteError("NetworkUnavailable");
  }
}

export class FakePronoteDataSource implements PronoteDataSource {
  readonly id = "fake";
  readonly strategy: string;
  private readonly latency: number;

  constructor(options: MockOptions = {}) {
    this.latency = options.latency ?? 550;
    this.strategy = "DirectPronoteStrategy";
  }

  private async delay(ms = this.latency) {
    await new Promise((resolve) => setTimeout(resolve, ms));
  }

  async authenticate({
    schoolUrl,
    username,
    password,
  }: {
    schoolUrl: string;
    username: string;
    password: string;
  }): Promise<PronoteAuthResult> {
    assertReachable();
    await this.delay();

    if (!schoolUrl.startsWith("http")) throw toPronoteError("InvalidPronoteUrl");
    if (/captcha|ent\./i.test(schoolUrl)) throw toPronoteError("CaptchaRequired");
    if (!username.trim() || password.length < 3) throw toPronoteError("InvalidCredentials");
    // Any password containing "erreur" simulates a PRONOTE outage for testing.
    if (/erreur|indispo/i.test(password)) throw toPronoteError("PronoteUnavailable");

    const payload = buildMockPayload(schoolUrl);
    const strategy = /ent\.|\.fr\/ent/i.test(schoolUrl)
      ? "EntStrategy"
      : /index-education|pronote/i.test(schoolUrl)
        ? "DirectPronoteStrategy"
        : "AlternativeStrategy";

    logger.info("Sync started", { strategy });

    return {
      token: `fake-token-${Date.now()}`,
      expiresAt: Date.now() + 1000 * 60 * 60 * 6,
      studentName: `${payload.student.prenom} ${payload.student.nom}`.trim(),
      schoolName: payload.student.etablissement ?? "Établissement",
      payload,
    };
  }

  async restoreSession(token: string, schoolUrl: string): Promise<PronoteAuthResult> {
    assertReachable();
    await this.delay(300);
    if (!token.startsWith("fake-token-")) throw toPronoteError("SessionExpired");

    const payload = buildMockPayload(schoolUrl);
    return {
      token,
      expiresAt: Date.now() + 1000 * 60 * 60 * 6,
      studentName: `${payload.student.prenom} ${payload.student.nom}`.trim(),
      schoolName: payload.student.etablissement ?? "Établissement",
      payload,
    };
  }

  async fetchAll(token: string, schoolUrl: string): Promise<PronoteRawPayload> {
    assertReachable();
    await this.delay(420);
    if (!token) throw toPronoteError("SessionExpired");

    const payload = buildMockPayload(schoolUrl);
    // Every third sync introduces a brand-new grade so "nouvelle note"
    // detection and the change-of-average notification can be demonstrated.
    if (Math.random() < 0.45) {
      payload.grades = [
        {
          id: `g-new-${Date.now()}`,
          matiere: "maths",
          intitule: "Nouvelle évaluation",
          note: 19,
          bareme: 20,
          coefficient: 2,
          date: isoDaysAgo(0),
          commentaire: "Excellent.",
        },
        ...payload.grades,
      ];
      payload.subjectAverages = payload.subjectAverages.map((a) =>
        a.matiere === "maths" ? { ...a, moyenne: 16.9 } : a,
      );
    }
    logger.info("Sync completed");
    return payload;
  }

  async logout(): Promise<void> {
    await this.delay(120);
  }
}

export const fakePronoteDataSource = new FakePronoteDataSource();
