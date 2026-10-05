import { describe, expect, it } from "vitest";
import { createHash } from "node:crypto";
import { existsSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * The download deliverable: the APK served by the site must exist, be a valid
 * APK archive, be branded Ardoise and never be a stale build.
 *
 * The shipped artefact is the RELEASE variant (not debuggable, no Compose
 * tooling inside), so the test compares against that output.
 */
const ROOT = process.cwd();
const GRADLE_APK = join(
  ROOT,
  "android/app/build/outputs/apk/release/app-release.apk",
);
const APK_PATH = join(ROOT, "public", "Ardoise.apk");

const md5 = (path: string) =>
  createHash("md5").update(readFileSync(path)).digest("hex");

describe("APK Ardoise téléchargeable depuis le site", () => {
  it("est présent dans public/ et pèse plus d'un mégaoctet", () => {
    expect(existsSync(APK_PATH)).toBe(true);
    expect(statSync(APK_PATH).size).toBeGreaterThan(1_000_000);
  });

  it("est une archive APK valide (en-tête PK)", () => {
    const header = [...readFileSync(APK_PATH).subarray(0, 4)];
    expect(header).toEqual([0x50, 0x4b, 0x03, 0x04]);
  });

  it("contient le manifest et les dex compilés", () => {
    const listing = readFileSync(APK_PATH).toString("latin1");
    expect(listing).toContain("AndroidManifest.xml");
    expect(listing).toContain("classes.dex");
  });

  it("est le build release courant, pas une copie obsolète", () => {
    expect(existsSync(GRADLE_APK)).toBe(true);
    expect(md5(APK_PATH)).toBe(md5(GRADLE_APK));
  });

  it("porte le nouveau nom, racine comme ancien chemin", () => {
    const rootCopy = join(ROOT, "Ardoise.apk");
    const legacyCopy = join(ROOT, "public", "Pronote.apk");
    expect(existsSync(rootCopy)).toBe(true);
    expect(existsSync(legacyCopy)).toBe(true);
    expect(md5(legacyCopy)).toBe(md5(APK_PATH));
  });

  it("est lié au téléchargement depuis la page d'accueil", () => {
    const app = readFileSync(join(ROOT, "src", "App.tsx"), "utf8");
    expect(app).toContain('"/Ardoise.apk"');
    expect(app).toContain('download={APK_NAME}');
    expect(app).not.toContain('"/Pronote.apk"');
  });

  it("expose une page d'accueil complète", () => {
    expect(existsSync(join(ROOT, "index.html"))).toBe(true);
    expect(existsSync(join(ROOT, "src", "main.tsx"))).toBe(true);
    expect(existsSync(join(ROOT, "src", "ErrorBoundary.tsx"))).toBe(true);
    expect(existsSync(join(ROOT, "src", "index.css"))).toBe(true);
  });
});