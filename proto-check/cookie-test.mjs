import forge from "node-forge";

const PRONOTE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 19_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 PRONOTE Mobile APP Version/2.0.11";
const FALLBACK_RSA_M = "B99B77A3D72D3A29B4271FC7B7300E2F791EB8948174BE7B8024667E915446D4EEA0C2424B8D1EBF7E2DDFF94691C6E994E839225C627D140A8F1146D1B0B5F18A09BBD3D8F421CA1E3E4796B301EEBCCF80D81A32A1580121B8294433C38377083C5517D5921E8A078CDC019B15775292EFDA2C30251B1CCABE812386C893E5";
const FALLBACK_RSA_E = "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010001";

const md5Hex = (b) => forge.md.md5.create().update(b).digest().toHex();
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

const base = "https://demo.index-education.net/pronote";
const pageUrl = `${base}/mobile.eleve.html?fd=1&login=true&bydlg=A6ABB224-12DD-4E31-AD3E-8A39A1C2C335`;

const pageRes = await fetch(pageUrl, { headers: { "User-Agent": PRONOTE_UA } });
const html = await pageRes.text();
const pageCookies = pageRes.headers.getSetCookie?.() ?? [];
const stripped = html.replace(/ /gu, "").replace(/\n/gu, "");
const start = JSON.parse(stripped.match(/Start\((\{[^)]*\})\)/)[1].replace(/(['"])?([a-z0-9A-Z_]+)(['"])?:/gu, '"$2": ').replace(/'/gu, '"'));
const id = parseInt(start.h);
const aesIV = forge.random.getBytesSync(16);
console.log("session", id, "| cookies page:", pageCookies);

const pub = forge.pki.rsa.setPublicKey(new forge.jsbn.BigInteger(FALLBACK_RSA_M, 16), new forge.jsbn.BigInteger(FALLBACK_RSA_E, 16));
const uuid = forge.util.encode64(pub.encrypt(hexToBytes(aesIV).getBytes()), 64);
const zeroIV = () => forge.util.createBuffer().fillWithByte(0, 16);
const aesIVBuf = () => forge.util.createBuffer(aesIV);
const cookieHeader = pageCookies.map((c) => c.split(";")[0]).join("; ");

const no1 = aesEncryptHex("1", forge.util.createBuffer(""), zeroIV());
const fpRes = await fetch(`${base}/appelfonction/6/${id}/${no1}`, {
  method: "POST",
  headers: { "Content-Type": "application/json", "User-Agent": PRONOTE_UA, ...(cookieHeader ? { Cookie: cookieHeader } : {}) },
  body: JSON.stringify({ session: id, no: no1, id: "FonctionParametres", dataSec: { data: { Uuid: uuid } } }),
});
const fpCookies = fpRes.headers.getSetCookie?.() ?? [];
console.log("FP →", fpRes.status, "| cookies FP:", fpCookies);
const fpRaw = await fpRes.text();
console.log("FP ok ?", fpRaw.includes("FonctionParametres") && !fpRaw.includes("Erreur"));

const fpCookiesHeader = fpCookies.map((c) => c.split(";")[0]).join("; ");
const allCookies = [cookieHeader, fpCookiesHeader].filter(Boolean).join("; ");

const ident = { genreConnexion: 0, genreEspace: 6, identifiant: "FAUXCOMPTE", pourENT: false, enConnexionAuto: false, demandeConnexionAuto: false, enConnexionAppliMobile: false, demandeConnexionAppliMobile: true, demandeConnexionAppliMobileJeton: false, uuidAppliMobile: "11111111-2222-4333-8444-555555555555", loginTokenSAV: "", informationsAppareil: { modele: "iPhone18,3", platforme: "iOS" } };

async function tryIdentification(label, ivBuf) {
  const no2 = aesEncryptHex("2", forge.util.createBuffer(""), ivBuf);
  const res = await fetch(`${base}/appelfonction/6/${id}/${no2}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "User-Agent": PRONOTE_UA, ...(allCookies ? { Cookie: allCookies } : {}) },
    body: JSON.stringify({ session: id, no: no2, id: "Identification", dataSec: { data: ident } }),
  });
  const raw = await res.text();
  console.log(`${label} → ${res.status} ${raw.slice(0, 150)}`);
}

// FP répété (ordre 2) pour tester si la session survit à une 2e requête quelconque
const no2fp = aesEncryptHex("2", forge.util.createBuffer(""), aesIVBuf());
const fp2Res = await fetch(`${base}/appelfonction/6/${id}/${no2fp}`, {
  method: "POST",
  headers: { "Content-Type": "application/json", "User-Agent": PRONOTE_UA, ...(allCookies ? { Cookie: allCookies } : {}) },
  body: JSON.stringify({ session: id, no: no2fp, id: "FonctionParametres", dataSec: { data: { Uuid: uuid } } }),
});
console.log("FP#2 (ordre 2, IV aes) →", fp2Res.status, (await fp2Res.text()).slice(0, 120));

await tryIdentification("Identification (ordre 3, IV aes, cookies)", aesIVBuf());
