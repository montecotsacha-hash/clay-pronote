/**
 * Ardoise — Worker du site.
 *
 * La page elle-même est un build statique (assets). L'APK, lui, n'est pas un
 * asset : il pèse ~11,4 Mio, au-dessus de la limite d'upload des assets, et il
 * doit pouvoir être remplacé sans redéployer le site. Il vit donc dans le
 * bucket R2 lié à `FILES` et il est servi d'ici, avec le support des requêtes
 * partielles (Range) qu'attendent les gestionnaires de téléchargement.
 *
 *   GET  /Ardoise.apk | /Pronote.apk   -> l'APK, depuis R2
 *   GET  /version.json                 -> le manifeste de version de l'APK
 *   PUT  /api/apk?name=Ardoise.apk     -> dépose un nouvel APK (jeton requis)
 *   tout le reste                      -> assets statiques, avec repli SPA
 *
 * Rien de sensible n'est écrit ici : le jeton vit dans le secret UPLOAD_TOKEN,
 * jamais dans le dépôt ni dans la réponse.
 */

const APK_NAMES = ["Ardoise.apk", "Pronote.apk"];
const APK_PATHS = APK_NAMES.map((name) => `/${name}`);
const APK_TYPE = "application/vnd.android.package-archive";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (path === "/api/apk") {
      if (request.method !== "PUT") {
        return json(405, "méthode non autorisée", { allow: "PUT" });
      }
      return uploadApk(request, env, url);
    }

    if (
      APK_PATHS.includes(path) &&
      (request.method === "GET" || request.method === "HEAD")
    ) {
      return serveApk(request, env, path.slice(1));
    }

    if (
      path === "/version.json" &&
      (request.method === "GET" || request.method === "HEAD")
    ) {
      return serveVersion(request, env);
    }

    return env.ASSETS.fetch(request);
  },
};

/** Sert l'APK depuis R2, en flux, avec Range si le client en demande un. */
async function serveApk(request, env, name) {
  // head() : métadonnées seulement, sans corps. Il faut un get() pour le flux,
  // sinon la réponse part avec un corps vide.
  const meta = await findApk(env, name);
  if (!meta) {
    return new Response(`APK introuvable : ${name}\n`, {
      status: 404,
      headers: { "content-type": "text/plain; charset=utf-8" },
    });
  }

  const headers = new Headers({
    "content-type": APK_TYPE,
    "accept-ranges": "bytes",
    "content-disposition": `attachment; filename="${name}"`,
    etag: meta.httpEtag,
    "cache-control": "public, max-age=300",
  });

  const range = parseRange(request.headers.get("range"), meta.size);
  const object = await env.FILES.get(
    meta.key,
    range
      ? { range: { offset: range.offset, length: range.length } }
      : undefined,
  );
  if (!object) {
    return new Response("objet disparu pendant la lecture\n", {
      status: 409,
      headers: { "content-type": "text/plain; charset=utf-8" },
    });
  }

  if (range) {
    // Sur une lecture partielle, `size` reste la taille de l'objet entier :
    // c'est `range` qui décrit ce qui a réellement été lu.
    const offset = object.range?.offset ?? range.offset;
    const length = object.range?.length ?? range.length;
    headers.set("content-length", String(length));
    headers.set(
      "content-range",
      `bytes ${offset}-${offset + length - 1}/${meta.size}`,
    );
    return new Response(request.method === "HEAD" ? null : object.body, {
      status: 206,
      headers,
    });
  }

  headers.set("content-length", String(meta.size));
  return new Response(request.method === "HEAD" ? null : object.body, {
    status: 200,
    headers,
  });
}

/**
 * Cherche l'objet de l'APK.
 *
 * Le nom exact (`Ardoise.apk`) a la priorité : c'est la clé qu'écrit
 * /api/apk. Le balayage du bucket n'existe que pour les objets déposés par la
 * version précédente du site, dont la clé nous est inconnue : sans lui, la mise
 * en ligne de ce Worker couperait le téléchargement jusqu'au premier upload.
 */
async function findApk(env, name) {
  const direct = await env.FILES.head(name);
  if (direct) return direct;

  const wanted = name.toLowerCase();
  const listed = await env.FILES.list({ limit: 1000 });
  const matches = listed.objects
    .filter((object) => object.key.toLowerCase().split("/").pop() === wanted)
    .sort(
      (a, b) => new Date(b.uploaded).getTime() - new Date(a.uploaded).getTime(),
    );

  return matches.length > 0 ? env.FILES.head(matches[0].key) : null;
}

/**
 * Le manifeste de version consommé par l'application Android : à chaque
 * lancement, elle compare `versionCode` au sien et propose la mise à jour.
 *
 * Les champs de version viennent des métadonnées posées par /api/apk. Sans
 * métadonnées (APK déposé par une ancienne version du site), ils valent null et
 * l'application reste silencieuse plutôt que de proposer une mise à jour fausse.
 */
async function serveVersion(request, env) {
  const meta = await findApk(env, "Ardoise.apk");
  if (!meta) {
    return json(404, "aucun APK publié pour l'instant");
  }

  const custom = meta.customMetadata ?? {};
  const body = {
    app: "Ardoise",
    versionName: custom.versionName ?? null,
    versionCode: custom.versionCode != null ? Number(custom.versionCode) : null,
    size: meta.size,
    sha256: custom.sha256 ?? null,
    apkUrl: "/Ardoise.apk",
    uploaded:
      meta.uploaded instanceof Date ? meta.uploaded.toISOString() : null,
    etag: meta.httpEtag,
  };

  return new Response(
    request.method === "HEAD" ? null : `${JSON.stringify(body)}\n`,
    {
      status: 200,
      headers: {
        "content-type": "application/json; charset=utf-8",
        // Court : une nouvelle version doit être visible en quelques minutes.
        "cache-control": "public, max-age=60",
      },
    },
  );
}

/** Dépose un APK dans R2. Réservé à qui présente le jeton. */
async function uploadApk(request, env, url) {
  if (!env.UPLOAD_TOKEN) {
    return json(503, "UPLOAD_TOKEN non configuré sur cet environnement");
  }

  const provided = bearer(request) ?? url.searchParams.get("token") ?? "";
  if (!constantTimeEquals(provided, env.UPLOAD_TOKEN)) {
    return json(403, "jeton invalide");
  }

  const name = (url.searchParams.get("name") ?? "").trim();
  if (!APK_NAMES.includes(name)) {
    return json(400, `nom refusé : attendu ${APK_NAMES.join(" ou ")}`);
  }

  const body = await request.arrayBuffer();
  // Un APK qui aurait été tronqué ou remplacé par une page d'erreur ne doit
  // jamais écraser le binaire en ligne.
  if (body.byteLength < 1_000_000) {
    return json(
      400,
      `corps trop petit pour un APK (${body.byteLength} octets)`,
    );
  }
  const header = new Uint8Array(body, 0, 4);
  if (
    !(
      header[0] === 0x50 &&
      header[1] === 0x4b &&
      header[2] === 0x03 &&
      header[3] === 0x04
    )
  ) {
    return json(400, "ce n'est pas une archive APK (en-tête PK absent)");
  }

  // Version publiée : optionnelle pour rester compatible avec l'ancien flux
  // d'upload, mais validée quand elle est fournie — ces valeurs sont lues par
  // l'application pour décider d'une mise à jour.
  const versionName = (url.searchParams.get("versionName") ?? "").trim();
  const versionCodeRaw = (url.searchParams.get("versionCode") ?? "").trim();
  const customMetadata = {};

  if (versionName !== "" || versionCodeRaw !== "") {
    if (!/^[0-9A-Za-z.+_-]{1,32}$/.test(versionName)) {
      return json(
        400,
        "versionName invalide (1 à 32 caractères alphanumériques)",
      );
    }
    const versionCode = Number(versionCodeRaw);
    if (
      !Number.isInteger(versionCode) ||
      versionCode < 1 ||
      versionCode > 2_147_483_647
    ) {
      return json(400, "versionCode invalide (entier positif)");
    }
    customMetadata.versionName = versionName;
    customMetadata.versionCode = String(versionCode);
    // Calculé ici plutôt que fourni : l'application ne reçoit jamais une
    // empreinte qui ne correspond pas au binaire réellement déposé.
    customMetadata.sha256 = await sha256Hex(body);
  }

  const object = await env.FILES.put(name, body, {
    httpMetadata: { contentType: APK_TYPE },
    customMetadata,
  });

  return json(200, "APK déposé", {
    name,
    size: body.byteLength,
    etag: object.httpEtag,
    versionName: customMetadata.versionName ?? null,
    versionCode: customMetadata.versionCode ?? null,
    sha256: customMetadata.sha256 ?? null,
  });
}

/** `bytes=0-1023`, `bytes=5000-`, `bytes=-1000` — une seule plage, sinon flux complet. */
function parseRange(header, size) {
  if (!header) return null;
  const match = /^bytes=(\d*)-(\d*)$/.exec(header.trim());
  if (!match) return null;

  const [, rawStart, rawEnd] = match;
  let offset;
  let length;

  if (rawStart === "") {
    const suffix = Number(rawEnd);
    if (!Number.isFinite(suffix) || suffix <= 0) return null;
    offset = Math.max(0, size - suffix);
    length = size - offset;
  } else {
    offset = Number(rawStart);
    const end = rawEnd === "" ? size - 1 : Number(rawEnd);
    if (
      !Number.isFinite(offset) ||
      !Number.isFinite(end) ||
      offset > end ||
      offset >= size
    ) {
      return null;
    }
    length = Math.min(end, size - 1) - offset + 1;
  }

  return length > 0 ? { offset, length } : null;
}

/** Empreinte SHA-256 hexadécimale du binaire, calculée par le runtime. */
async function sha256Hex(body) {
  const digest = await crypto.subtle.digest("SHA-256", body);
  return [...new Uint8Array(digest)]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function bearer(request) {
  const header = request.headers.get("authorization") ?? "";
  const match = /^Bearer\s+(.+)$/i.exec(header.trim());
  return match ? match[1].trim() : null;
}

/** Comparaison à durée constante : ne laisse pas fuiter le jeton par le timing. */
function constantTimeEquals(a, b) {
  const left = new TextEncoder().encode(a);
  const right = new TextEncoder().encode(b);
  if (left.length !== right.length) return false;
  let diff = 0;
  for (let i = 0; i < left.length; i += 1) diff |= left[i] ^ right[i];
  return diff === 0;
}

function json(status, message, extra = {}) {
  return new Response(`${JSON.stringify({ message, ...extra })}\n`, {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}
