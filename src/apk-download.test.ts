import { describe, expect, it } from "vitest";
import { existsSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * The download deliverable: public/Pronote.apk must be served at /Pronote.apk
 * by Vite, so it has to exist, be a real APK archive and be linked from the
 * landing page.
 */
const APK_PATH = join(process.cwd(), "public", "Pronote.apk");

describe("APK téléchargeable depuis le site", () => {
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

  it("est aussi livré à la racine du projet", () => {
    expect(existsSync(join(process.cwd(), "Pronote.apk"))).toBe(true);
  });

  it("est lié au téléchargement depuis la page d'accueil", () => {
    const app = readFileSync(join(process.cwd(), "src", "App.tsx"), "utf8");
    expect(app).toContain('"/Pronote.apk"');
    expect(app).toContain('download="Pronote.apk"');
  });

  it("expose une page d'accueil complète", () => {
    expect(existsSync(join(process.cwd(), "index.html"))).toBe(true);
    expect(existsSync(join(process.cwd(), "src", "main.tsx"))).toBe(true);
    expect(existsSync(join(process.cwd(), "src", "index.css"))).toBe(true);
  });
});