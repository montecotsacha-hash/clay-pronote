import forge from "node-forge";
import pako from "pako";

const PRONOTE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 19_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 PRONOTE Mobile APP Version/2.0.11";
const FALLBACK_RSA_M = "B99B77A3D72D3A29B4271FC7B7300E2F791EB8948174BE7B8024667E915446D4EEA0C2424B8D1EBF7E2DDFF94691C6E994E839225C627D140A8F1146D1B0B5F18A09BBD3D8F421CA1E3E4796B301EEBCCF80D81A32A1580121B8294433C38377083C5517D5921E8A078CDC019B15775292EFDA2C30251B1CCABE812386C893E5";
const FALLBACK_RSA_E = "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010001";

const cleanUrl = (raw) => {
  const u = new URL(raw);
  const parts = u.pathname.split("/").filter(Boolean);
  if (parts.length && parts[parts.length - 1].includes(".html")) parts.pop();
  return `${u.protocol}//${u.host}/${parts.join("/")}`;
};

function parseStart(html) {
  const stripped = html.replace(/ /gu, "").replace(/\n/gu, "");
  const m = stripped.match(/Start\((\{[^)]*\})\)/);
  if (!m) throw new Error("PageUnavailableError (Start introuvable)");
  return JSON.parse(m[1].replace(/(['"])?([a-z0-9A-Z_]+)(['"])?:/gu, '"$2": ').replace(/'/gu, '"'));
}

const md5Bytes = (b) => forge.md.md5.create().update(b).digest().getBytes();
const hexToBytes = (hex) => forge.util.createBuffer(forge.util.binary.hex.decode(hex));
const bytesFromBinary = (s) => Uint8Array.from(s, (c) => c.charCodeAt(0) & 0xff);

async function main() {
  const base = cleanUrl("https://demo.index-education.net/pronote/eleve.html");
  const pageRes = await fetch(`${base}/mobile.elele.html`.replace("elele", "eleve") + "?fd=1&login=true&bydlg=A6ABB224-12DD-4E31-AD3E-8A39A1C2C335", { headers: { "User-Agent": PRONOTE_UA } });
  const html = await pageRes.text();
  const version = html.match(/>PRONOTE (\d+\.\d+\.\d+)/)[1].split(".").map(Number);
  const start = parseStart(html);
  const hasRsaKeys = Boolean(start.MR && start.ER);
  console.log("version:", version.join("."), "| session:", start.h, "| MR/ER présents:", hasRsaKeys, "| http:", start.http ?? false);

  const encryptRequests = Boolean(start.CrA);
  const compressRequests = Boolean(start.CoA);
  const ivTemp = forge.random.getBytesSync(16);   // IV temporaire
  const realIV = md5Bytes(ivTemp);                 // IV réel pour tout sauf l'ordre 1
  const keyZero = md5Bytes("");                    // clé AES initiale = MD5("")

  // Uuid : RSA uniquement si http ou clés MR/ER fournies ; sinon IV brut encodé base64.
  let uuid;
  if (start.http || hasRsaKeys) {
    const modulus = hasRsaKeys ? start.MR : FALLBACK_RSA_M;
    const exponent = hasRsaKeys ? start.ER : FALLBACK_RSA_E;
    const pub = forge.pki.rsa.setPublicKey(new forge.jsbn.BigInteger(modulus, 16), new forge.jsbn.BigInteger(exponent, 16));
    uuid = forge.util.encode64(pub.encrypt(ivTemp), 64);
  } else {
    uuid = forge.util.encode64(ivTemp);
  }
  console.log("Uuid (mode):", start.http || hasRsaKeys ? "RSA" : "brut base64");

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
    c.update(forge.util.createBuffer(hexToBytes(hex)));
    c.finish();
    return c.output.getBytes();
  };

  let requestNumber = 1;
  const post = async (name, dataInner, opts = {}) => {
    const no = enc(String(requestNumber), keyZero, requestNumber === 1 ? forge.util.createBuffer().fillWithByte(0, 16).getBytes() : realIV);
    const url = `${base}/appelfonction/${start.a}/${start.h}/${no}`;
    requestNumber += 2;
    let dataSec = JSON.stringify(dataInner);
    if (compressRequests) dataSec = forge.util.bytesToHex(pako.deflateRaw(forge.util.createBuffer(dataSec).toHex(), { level: 6 }));
    if (encryptRequests) dataSec = enc(compressRequests ? hexToBytes(dataSec).getBytes() : dataSec, keyZero, realIV);
    else dataSec = JSON.parse(dataSec);
    const res = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json", "User-Agent": PRONOTE_UA }, body: JSON.stringify({ session: parseInt(start.h), no, id: name, dataSec }) });
    const raw = await res.text();
    let outer = JSON.parse(raw);
    if (outer.Erreur) throw new Error(`Erreur Pronote G=${outer.Erreur.G} : ${outer.Erreur.Titre}`);
    let d = outer.dataSec;
    const respKey = opts.responseKey ?? keyZero;
    const respIV = opts.responseIV ?? realIV;
    if (encryptRequests) {
      d = dec(d, respKey, respIV);
      if (!compressRequests) return JSON.parse(d);
      d = forge.util.bytesToHex(d);
    }
    if (compressRequests) {
      d = pako.inflateRaw(bytesFromBinary(hexToBytes(d).getBytes()), { to: "string" });
      return JSON.parse(d);
    }
    return d;
  };

  // --- Ordre 1 : FonctionParametres ---
  const fpData = await post("FonctionParametres", { data: { Uuid: uuid, identifiantNav: null } });
  const fp = fpData.data || fpData.donnees;
  console.log("FonctionParametres OK — versionPN:", fp?.General?.versionPN);

  // --- Ordre 3 : Identification ---
  const ident = { genreConnexion: 0, genreEspace: start.a, identifiant: "FAUXCOMPTE", pourENT: false, enConnexionAuto: false, demandeConnexionAuto: false, enConnexionAppliMobile: false, demandeConnexionAppliMobile: true, demandeConnexionAppliMobileJeton: false, uuidAppliMobile: "11111111-2222-4333-8444-555555555555", loginTokenSAV: "", informationsAppareil: { modele: "iPhone18,3", platforme: "iOS" } };
  const idr = await post("Identification", ident);
  const idd = idr.data || idr.donnees;
  console.log("Identification OK — challenge reçu, modeCompLog:", idd.modeCompLog, "modeCompMdp:", idd.modeCompMdp);

  // --- Ordre 5 : Authentification (mauvais mot de passe → Acces=1 attendu) ---
  const user = idd.modeCompLog ? "fauxcompte" : "FAUXCOMPTE";
  const pwd = idd.modeCompMdp ? "mauvaismotdepasse" : "MauvaisMotDePasse";
  const motdepasse = forge.md.sha256.create().update((idd.alea ?? "") + pwd).digest().toHex().toUpperCase();
  const challengeKey = md5Bytes(forge.util.encodeUtf8(user + motdepasse));
  const challenge = enc(idd.challenge, challengeKey, realIV);
  const auth = await post("Authentification", { connexion: 0, challenge, espace: start.a });
  const ad = auth.data || auth.donnees;
  if (ad.Acces !== undefined && ad.Acces !== 0) {
    console.log(">>> Acces =", ad.Acces, ad.AccesMessage ? JSON.stringify(ad.AccesMessage) : "");
    if (ad.Acces === 1) console.log(">>> PARFAIT : Acces=1 (BadCredentials) → tout le protocole fonctionne jusqu'à la vérification du mot de passe !");
    process.exit(0);
  }
  console.log("Réponse inattendue :", Object.keys(ad));
}

main().catch((e) => { console.error("ÉCHEC:", e.message); process.exit(1); });
