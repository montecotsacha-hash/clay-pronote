import forge from "node-forge";

const PRONOTE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 19_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 PRONOTE Mobile APP Version/2.0.11";

const cleanUrl = (raw) => {
  const u = new URL(raw);
  const parts = u.pathname.split("/").filter(Boolean);
  if (parts.length && parts[parts.length - 1].includes(".html")) parts.pop();
  return `${u.protocol}//${u.host}/${parts.join("/")}`;
};

/** Parse `Start ( {...} );}` — tolère espaces et parenthèse finale (2026). */
function parseStart(html) {
  const idx = html.indexOf("Start");
  if (idx < 0) throw new Error("PageUnavailableError (Start introuvable)");
  const start = html.indexOf("{", idx);
  if (start < 0) throw new Error("PageUnavailableError (accolade introuvable)");
  let depth = 0;
  let inString = false;
  let quote = "";
  for (let i = start; i < html.length; i += 1) {
    const c = html[i];
    if (inString) {
      if (c === "\\") { i += 1; continue; }
      if (c === quote) inString = false;
      continue;
    }
    if (c === '"' || c === "'") { inString = true; quote = c; continue; }
    if (c === "{") depth += 1;
    else if (c === "}") {
      depth -= 1;
      if (depth === 0) {
        const jsonish = html.slice(start, i + 1)
          .replace(/(['"])?([a-zA-Z0-9_]+)(['"])?\s*:/gu, '"$2":')
          .replace(/'/gu, '"');
        return JSON.parse(jsonish);
      }
    }
  }
  throw new Error("PageUnavailableError (JSON Start incomplet)");
}

const md5Bytes = (b) => forge.md.md5.create().update(b).digest().getBytes();
const hexToBytes = (hex) => forge.util.createBuffer(forge.util.binary.hex.decode(hex)).getBytes();

async function main() {
  const base = cleanUrl("https://demo.index-education.net/pronote/eleve.html");
  // Jar de cookies minimal (requests.Session le fait ; le serveur lie la session à un cookie).
  const cookieJar = new Map();
  const eatCookies = (res) => {
    for (const c of res.headers.getSetCookie?.() ?? []) {
      const [pair] = c.split(";");
      const eq = pair.indexOf("=");
      if (eq > 0) cookieJar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
    }
  };
  const cookieHeader = () => [...cookieJar].map(([k, v]) => `${k}=${v}`).join("; ");
  const pageRes = await fetch(`${base}/mobile.eleve.html?fd=1&login=true&bydlg=A6ABB224-12DD-4E31-AD3E-8A39A1C2C335`, { headers: { "User-Agent": PRONOTE_UA } });
  eatCookies(pageRes);
  const html = await pageRes.text();
  const version = html.match(/>PRONOTE (\d+\.\d+\.\d+)/)[1].split(".").map(Number);
  const start = parseStart(html);
  const encryptRequests = Boolean(start.CrA);
  const compressRequests = Boolean(start.CoA);
  console.log("version:", version.join("."), "| session:", start.h, "| CrA:", start.CrA, "| CoA:", start.CoA);

  const ivTemp = forge.random.getBytesSync(16);
  const realIV = md5Bytes(ivTemp);
  let sessionKey = md5Bytes(""); // clé initiale = MD5("")
  const uuid = forge.util.encode64(ivTemp); // pas de MR/ER ni http → IV brut base64

  const enc = (plain, key, iv) => {
    const c = forge.cipher.createCipher("AES-CBC", key);
    c.start({ iv: forge.util.createBuffer(iv) });
    c.update(forge.util.createBuffer(plain));
    c.finish();
    return c.output.toHex();
  };
  const dec = (hex, key, iv) => {
    const c = forge.cipher.createDecipher("AES-CBC", key);
    c.start({ iv: forge.util.createBuffer(iv) });
    c.update(forge.util.createBuffer(forge.util.binary.hex.decode(hex)));
    c.finish();
    return c.output.getBytes();
  };

  let requestNumber = 1;
  let puRaw;
  const post = async (name, dataSecObject) => {
    const zeroIV = forge.util.createBuffer().fillWithByte(0, 16).getBytes();
    const no = enc(String(requestNumber), sessionKey, requestNumber === 1 ? zeroIV : realIV);
    const url = `${base}/appelfonction/${start.a}/${start.h}/${no}`;
    requestNumber += 2;
    let dataSec = JSON.stringify(dataSecObject);
    if (encryptRequests) dataSec = enc(forge.util.createBuffer(dataSec).getBytes(), sessionKey, realIV);
    else dataSec = JSON.parse(dataSec);
    const res = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json", "User-Agent": PRONOTE_UA, ...(cookieJar.size ? { Cookie: cookieHeader() } : {}) }, body: JSON.stringify({ session: parseInt(start.h), no, id: name, dataSec }) });
    eatCookies(res);
    const raw = await res.text();
    puRaw = raw;
    console.log(`[DIAG] ${name}: HTTP ${res.status} | ${raw.slice(0, 110).replace(/\n/g, " ")}`);
    if (raw.includes("La page a expir")) throw new Error("SessionExpired (page expirée)");
    if (raw.includes("Votre adresse IP ")) throw new Error("SuspendedIP");
    if (raw.includes("Vous avez d")) throw new Error("RateLimited");
    let outer;
    try { outer = JSON.parse(raw); } catch { throw new Error(`Réponse non-JSON: ${raw.slice(0, 200)}`); }
    if (outer.Erreur) throw new Error(`Erreur Pronote G=${outer.Erreur.G}: ${outer.Erreur.Titre}`);
    let d = outer.dataSec;
    if (encryptRequests) d = dec(d, sessionKey, realIV);
    if (typeof d === "string") d = JSON.parse(d);
    return d;
  };

  // --- Ordre 1 : FonctionParametres ---
  const fpData = await post("FonctionParametres", { data: { Uuid: uuid, identifiantNav: null }, donnees: { Uuid: uuid, identifiantNav: null } });
  const fp = fpData.data || fpData.donnees;
  console.log("FonctionParametres OK — versionPN:", fp?.General?.versionPN);

  // --- Ordre 3 : Identification (flux appli mobile) ---
  const ident = {
    genreConnexion: 0, genreEspace: start.a, identifiant: "demonstration", pourENT: false,
    enConnexionAuto: false, demandeConnexionAuto: false,
    enConnexionAppliMobile: false, demandeConnexionAppliMobile: true, demandeConnexionAppliMobileJeton: false,
    uuidAppliMobile: "11111111-2222-4333-8444-555555555555", loginTokenSAV: "",
    informationsAppareil: { modele: "iPhone18,3", platforme: "iOS" },
  };
  const idr = await post("Identification", { data: ident });
  const idd = idr.data || idr.donnees;
  console.log("Identification OK — challenge", String(idd.challenge).length, "car | modeCompLog:", idd.modeCompLog, "| modeCompMdp:", idd.modeCompMdp);

  // --- Ordre 5 : Authentification (challenge clair chiffré) ---
  const user = idd.modeCompLog ? "demonstration".toLowerCase() : "demonstration";
  const pwd = idd.modeCompMdp ? "pronotevs".toLowerCase() : "pronotevs";
  const hash = forge.md.sha256.create().update((idd.alea ?? "") + pwd).digest().toHex().toUpperCase();
  const challengeKey = md5Bytes(forge.util.encodeUtf8(user + hash));
  const challengeEnc = enc(forge.util.encodeUtf8(String(idd.challenge)), challengeKey, realIV);
  const auth = await post("Authentification", { data: { connexion: 0, challenge: challengeEnc, espace: start.a } });
  const ad = auth.data || auth.donnees;
  if (typeof ad.Acces === "number" && ad.Acces !== 0) {
    console.log("ÉCHEC LOGIN — Acces:", ad.Acces, ad.AccesMessage ? JSON.stringify(ad.AccesMessage) : "");
    process.exit(1);
  }
  console.log("Authentification OK — clés réponse:", Object.keys(ad).join(", "));

  // --- Clé de session depuis `cle` ---
  const cleRaw = String(ad.cle);
  const cleText = dec(cleRaw, challengeKey, realIV);
  console.log("cle déchiffrée (brute):", JSON.stringify(cleText.slice(0, 80)));
  let keyString;
  if (/^(\d+(,\d+)*)$/.test(cleText)) {
    // Les codes SONT les octets de la clé (pronotepy _enBytes / forge createBuffer latin-1).
    keyString = cleText.split(",").map((n) => String.fromCharCode(parseInt(n))).join("");
    sessionKey = md5Bytes(keyString);
    console.log("  format: octets bruts (", keyString.length, "octets ) → md5 ✓");
  } else {
    keyString = cleText;
    sessionKey = md5Bytes(forge.util.encodeUtf8(keyString));
    console.log("  format: chaîne brute → md5 ✓");
  }
  console.log("clé de session établie ✓");

  // --- Ordre 7 : ParametresUtilisateur (première requête authentifiée) ---
  console.log("[DIAG] cookies envoyés:", cookieHeader() || "(aucun)");
  try {
    const pu = await post("ParametresUtilisateur", {});
    var pud = pu.data || pu.donnees;
  } catch (e) {
    console.log("[DIAG] échec PU:", e.message);
    if (puRaw) console.log("[DIAG] corps:", String(puRaw).slice(0, 300));
    process.exit(1);
  }
  const ressource = pud.ressource;
  console.log("ParametresUtilisateur OK —", ressource?.L, "|", ressource?.Etablissement?.V?.L, "| classe:", ressource?.classeDEleve?.L ?? "(n/a)");
  const onglet0 = ressource?.listeOngletsPourPeriodes?.V?.[0];
  const periodes = onglet0?.listePeriodes?.V?.map((p) => `${p.N}:${p.L}`) ?? [];
  const defaut = onglet0?.periodeParDefaut?.V;
  console.log("  périodes:", periodes.join(" | "), "| par défaut:", defaut ? `${defaut.N}:${defaut.L}` : "(aucune)");

  // --- Ordre 9 : DernieresNotes ---
  const periode = { N: defaut.N, L: defaut.L };
  const dn = await post("DernieresNotes", { Signature: { onglet: 198 }, data: { Periode: periode } });
  const dnd = dn.data || dn.donnees;
  const devoirs = dnd.listeDevoirs?.V ?? [];
  const services = dnd.listeServices?.V ?? [];
  console.log("DernieresNotes OK —", devoirs.length, "notes |", services.length, "services | moyGénérale:", dnd.moyGenerale?.V ?? "(masquée)");
  devoirs.slice(0, 3).forEach((d) => {
    console.log("  note:", d.note?.V, "/", d.bareme?.V, "|", d.service?.V?.L, "|", d.date?.V, "| coef", d.coefficient);
  });
  console.log("\n=== PROTOCOLE 2026 ENTIÈREMENT VALIDÉ (login + données réelles) ===");
}

main().catch((e) => { console.error("ÉCHEC:", e.message); process.exit(1); });
