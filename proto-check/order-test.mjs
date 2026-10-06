import forge from "node-forge";
import pako from "pako";

const PRONOTE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 19_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 PRONOTE Mobile APP Version/2.0.11";
const FALLBACK_RSA_M = "B99B77A3D72D3A29B4271FC7B7300E2F791EB8948174BE7B8024667E915446D4EEA0C2424B8D1EBF7E2DDFF94691C6E994E839225C627D140A8F1146D1B0B5F18A09BBD3D8F421CA1E3E4796B301EEBCCF80D81A32A1580121B8294433C38377083C5517D5921E8A078CDC019B15775292EFDA2C30251B1CCABE812386C893E5";
const FALLBACK_RSA_E = "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010001";

const md5Hex = (bytes) => forge.md.md5.create().update(bytes).digest().toHex();
const hexToBytes = (hex) => forge.util.createBuffer(forge.util.binary.hex.decode(hex));
const aesEncryptHex = (plain, keyBytes, ivBytes) => {
  const k = hexToBytes(md5Hex(keyBytes));
  const iv = ivBytes.length() ? ivBytes : forge.util.createBuffer().fillWithByte(0, 16);
  const c = forge.cipher.createCipher("AES-CBC", k);
  c.start({ iv });
  c.update(forge.util.createBuffer(plain));
  c.finish();
  return c.output.toHex();
};
const aesDecrypt = (hex, keyBytes, ivBytes) => {
  const k = hexToBytes(md5Hex(keyBytes));
  const iv = ivBytes.length() ? ivBytes : forge.util.createBuffer().fillWithByte(0, 16);
  const c = forge.cipher.createDecipher("AES-CBC", k);
  c.start({ iv });
  c.update(forge.util.createBuffer(hexToBytes(hex)));
  c.finish();
  return c.output.getBytes();
};

const base = "https://demo.index-education.net/pronote";
const pageUrl = `${base}/mobile.eleve.html?fd=1&login=true&bydlg=A6ABB224-12DD-4E31-AD3E-8A39A1C2C335`;
const pageRes = await fetch(pageUrl, { headers: { "User-Agent": PRONOTE_UA } });
const html = await pageRes.text();
const setCookies = pageRes.headers.getSetCookie?.() ?? [];
const version = html.match(/>PRONOTE (\d+\.\d+\.\d+)/)[1].split(".").map(Number);
const stripped = html.replace(/ /gu, "").replace(/\n/gu, "");
const startJson = stripped.match(/Start\((\{[^)]*\})\)/)[1].replace(/(['"])?([a-z0-9A-Z_]+)(['"])?:/gu, '"$2": ').replace(/'/gu, '"');
const start = JSON.parse(startJson);
const aesIV = forge.random.getBytesSync(16);
const info = { id: parseInt(start.h), kind: start.a, url: base, aesIV, aesKey: "", order: 0, skip: !start.CrA };
console.log("session", info.id, "kind", info.kind, "skip", info.skip, "| cookies page:", setCookies.length);

const pub = forge.pki.rsa.setPublicKey(new forge.jsbn.BigInteger(FALLBACK_RSA_M, 16), new forge.jsbn.BigInteger(FALLBACK_RSA_E, 16));
const uuid = forge.util.encode64(pub.encrypt(hexToBytes(aesIV).getBytes()), 64);

const key0 = forge.util.createBuffer(""); // clé AES pas encore établie
const orderEnc = (n, ivBuf) => aesEncryptHex(String(n), key0, ivBuf);
const zeroIV = () => forge.util.createBuffer().fillWithByte(0, 16);
const randIV = () => forge.util.createBuffer(aesIV);

async function appel(orderObj, name, dataInner, ivChoice) {
  info.order++;
  const no = orderEnc(info.order, ivChoice === "zero" ? zeroIV() : randIV());
  const url = `${base}/appelfonction/${info.kind}/${info.id}/${no}`;
  const body = { session: info.id, no, id: name, dataSec: info.skip ? { data: dataInner } : "ENC" };
  const res = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json", "User-Agent": PRONOTE_UA }, body: JSON.stringify(body) });
  const raw = await res.text();
  return { res, raw, no };
}

// FonctionParametres (ordre 1, IV zéro)
const fp = await appel(info, "FonctionParametres", { Uuid: uuid, identifiantNav: null }, "zero");
console.log("FP →", fp.res.status, fp.raw.slice(0, 120));
const fpJson = JSON.parse(fp.raw);
const fpNo = fpJson?.no;
if (fpNo && info.skip) {
  try {
    const dec = aesDecrypt(fpNo, key0, zeroIV());
    console.log("no de la réponse FP déchiffré (IV zéro):", JSON.stringify(dec));
  } catch { console.log("déchiffrement no FP (IV zéro) échoué"); }
  try {
    const dec2 = aesDecrypt(fpNo, key0, randIV());
    console.log("no de la réponse FP déchiffré (IV aes):", JSON.stringify(dec2));
  } catch { console.log("déchiffrement no FP (IV aes) échoué"); }
}

// Identification : ordre 2, variante IV
const ident = { genreConnexion: 0, genreEspace: info.kind, identifiant: "FAUXCOMPTE", pourENT: false, enConnexionAuto: false, demandeConnexionAuto: false, enConnexionAppliMobile: false, demandeConnexionAppliMobile: true, demandeConnexionAppliMobileJeton: false, uuidAppliMobile: "11111111-2222-4333-8444-555555555555", loginTokenSAV: "", informationsAppareil: { modele: "iPhone18,3", platforme: "iOS" } };
for (const variant of ["zero", "aes"]) {
  // refaire une session propre à chaque variante pour ne pas fausser le compteur
  const p2 = await fetch(pageUrl, { headers: { "User-Agent": PRONOTE_UA } });
  const h2 = await p2.text();
  const st2 = JSON.parse(h2.replace(/ /gu, "").replace(/\n/gu, "").match(/Start\((\{[^)]*\})\)/)[1].replace(/(['"])?([a-z0-9A-Z_]+)(['"])?:/gu, '"$2": ').replace(/'/gu, '"'));
  const info2 = { id: parseInt(st2.h), kind: st2.a, order: 0 };
  const ivb = forge.util.createBuffer(aesIV);
  const no1 = aesEncryptHex("1", forge.util.createBuffer(""), forge.util.createBuffer().fillWithByte(0, 16));
  await fetch(`${base}/appelfonction/${info2.kind}/${info2.id}/${no1}`, { method: "POST", headers: { "Content-Type": "application/json", "User-Agent": PRONOTE_UA }, body: JSON.stringify({ session: info2.id, no: no1, id: "FonctionParametres", dataSec: { data: { Uuid: uuid, identifiantNav: null } } }) });
  const no2 = aesEncryptHex("2", forge.util.createBuffer(""), variant === "zero" ? forge.util.createBuffer().fillWithByte(0, 16) : ivb);
  const res2 = await fetch(`${base}/appelfonction/${info2.kind}/${info2.id}/${no2}`, { method: "POST", headers: { "Content-Type": "application/json", "User-Agent": PRONOTE_UA }, body: JSON.stringify({ session: info2.id, no: no2, id: "Identification", dataSec: { data: ident } }) });
  const raw2 = await res2.text();
  console.log(`Identification (ordre 2, IV ${variant}) →`, res2.status, raw2.slice(0, 160));
}
