import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import { VERSION, VERSION_CODENAME } from "./version";

const read = (path: string) =>
  readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

const changelog = read("CHANGELOG.md");
const gradle = read("android/app/build.gradle.kts");

/** Lignes du journal au format `- \`1.5.0\` — « Boussole » — …`. */
const entries = [...changelog.matchAll(/^- `([^`]+)` — « ([^»]+) »/gm)].map(
  (match) => ({ version: match[1], name: match[2] }),
);

describe("nommage des versions", () => {
  it("donne un nom à chaque version, sans jamais le réutiliser", () => {
    expect(entries.length).toBeGreaterThan(0);
    const names = entries.map((entry) => entry.name);
    expect(new Set(names).size).toBe(names.length);
    const versions = entries.map((entry) => entry.version);
    expect(new Set(versions).size).toBe(versions.length);
  });

  it("garde le site, le journal et le build Android sur la même version", () => {
    expect(changelog).toContain(`- \`${VERSION}\` — « ${VERSION_CODENAME} »`);
    expect(gradle).toContain(`versionName = "${VERSION}"`);
  });
});
