import { describe, expect, it } from "vitest";
import { createHash } from "node:crypto";
import worker from "../deploy/worker.js";

/**
 * The update manifest: the Android app polls /version.json at launch and offers
 * an update when the published versionCode is ahead of the installed one.
 *
 * These tests drive the REAL worker handler against a fake R2 bucket: the
 * version fields must come from the metadata written at upload time, and a
 * manifest without them must stay silent rather than invent a version.
 */

const SITE = "https://site.example";
const TOKEN = "jeton-de-test";

type Stored = {
  body: ArrayBuffer;
  customMetadata: Record<string, string>;
  uploaded: Date;
};

function fakeEnv(objects: Record<string, Stored> = {}) {
  return {
    UPLOAD_TOKEN: TOKEN,
    ASSETS: {
      fetch: async () => new Response("asset", { status: 200 }),
    },
    FILES: {
      head: async (key: string) => {
        const found = objects[key];
        if (!found) return null;
        return {
          key,
          size: found.body.byteLength,
          httpEtag: `"etag-${key}"`,
          uploaded: found.uploaded,
          customMetadata: found.customMetadata,
        };
      },
      list: async () => ({ objects: [] }),
      get: async () => null,
      put: async (
        key: string,
        body: ArrayBuffer,
        opts: { customMetadata?: Record<string, string> },
      ) => {
        objects[key] = {
          body,
          customMetadata: opts.customMetadata ?? {},
          uploaded: new Date("2026-10-18T10:00:00Z"),
        };
        return { httpEtag: `"etag-${key}"` };
      },
    },
  };
}

function apkBody(size = 1_200_000): ArrayBuffer {
  const bytes = new Uint8Array(size);
  bytes.set([0x50, 0x4b, 0x03, 0x04]); // PK\x03\x04 : une vraie archive
  return bytes.buffer;
}

function put(url: string, body: ArrayBuffer, token?: string) {
  const headers: Record<string, string> = {};
  if (token) headers.authorization = `Bearer ${token}`;
  return worker.fetch(
    new Request(url, { method: "PUT", body, headers }),
    fakeEnv() as never,
  );
}

describe("GET /version.json", () => {
  it("annonce la version déposée à l'upload", async () => {
    const env = fakeEnv({
      "Ardoise.apk": {
        body: apkBody(),
        customMetadata: {
          versionName: "1.5.0",
          versionCode: "12",
          sha256: "ab12",
        },
        uploaded: new Date("2026-10-18T09:40:00Z"),
      },
    });

    const response = await worker.fetch(
      new Request(`${SITE}/version.json`),
      env as never,
    );

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("application/json");
    // Court : une nouvelle version doit être visible en quelques minutes.
    expect(response.headers.get("cache-control")).toBe("public, max-age=60");

    const manifest = await response.json();
    expect(manifest).toMatchObject({
      app: "Ardoise",
      versionName: "1.5.0",
      versionCode: 12,
      sha256: "ab12",
      apkUrl: "/Ardoise.apk",
      uploaded: "2026-10-18T09:40:00.000Z",
    });
    expect(manifest.size).toBe(1_200_000);
  });

  it("reste silencieux sur la version quand l'APK est sans métadonnées", async () => {
    const env = fakeEnv({
      "Ardoise.apk": {
        body: apkBody(),
        customMetadata: {},
        uploaded: new Date(),
      },
    });

    const manifest = await (
      await worker.fetch(new Request(`${SITE}/version.json`), env as never)
    ).json();

    // null plutôt qu'une valeur inventée : l'app ne proposera pas de fausse màj.
    expect(manifest.versionName).toBeNull();
    expect(manifest.versionCode).toBeNull();
    expect(manifest.size).toBe(1_200_000);
  });

  it("annonce l'absence d'APK publié", async () => {
    const response = await worker.fetch(
      new Request(`${SITE}/version.json`),
      fakeEnv() as never,
    );
    expect(response.status).toBe(404);
  });
});

describe("PUT /api/apk", () => {
  it("range l'APK avec sa version, son code de version et son empreinte", async () => {
    const body = apkBody();
    const response = await put(
      `${SITE}/api/apk?name=Ardoise.apk&versionName=1.5.0&versionCode=12`,
      body,
      TOKEN,
    );

    expect(response.status).toBe(200);
    const stored = await response.json();
    const expectedSha = createHash("sha256")
      .update(Buffer.from(body))
      .digest("hex");
    expect(stored).toMatchObject({
      name: "Ardoise.apk",
      versionName: "1.5.0",
      versionCode: "12",
      sha256: expectedSha,
    });
  });

  it("refuse un versionCode qui n'est pas un entier positif", async () => {
    const response = await put(
      `${SITE}/api/apk?name=Ardoise.apk&versionName=1.5.0&versionCode=0`,
      apkBody(),
      TOKEN,
    );
    expect(response.status).toBe(400);
  });

  it("refuse un versionName exotique avant qu'il ne soit servi à l'app", async () => {
    const response = await put(
      `${SITE}/api/apk?name=Ardoise.apk&versionName=${encodeURIComponent("<script>")}&versionCode=12`,
      apkBody(),
      TOKEN,
    );
    expect(response.status).toBe(400);
  });

  it("reste compatible avec l'ancien flux sans paramètres de version", async () => {
    const response = await put(
      `${SITE}/api/apk?name=Pronote.apk`,
      apkBody(),
      TOKEN,
    );
    expect(response.status).toBe(200);
    const stored = await response.json();
    expect(stored.versionName).toBeNull();
  });

  it("exige le jeton d'upload", async () => {
    const response = await put(`${SITE}/api/apk?name=Ardoise.apk`, apkBody());
    expect(response.status).toBe(403);
  });
});
