import { chromium } from "playwright";
import { mkdirSync } from "node:fs";

const PREVIEW = process.env.PREVIEW_URL ?? "http://localhost:5173/";
const OUT = "screenshots";

const EXPECTED = {
  title: "Tes notes, enfin lisibles.",
  paragraph:
    "Ardoise est l'application mobile qui suit tes notes et ta moyenne Pronote — hors ligne, en notifications locales, sans jamais envoyer tes identifiants à un serveur.",
};

const viewports = [
  { name: "desktop", width: 1280, height: 900 },
  { name: "mobile", width: 390, height: 844 },
];

mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ args: ["--no-sandbox"] });
const report = { url: PREVIEW, viewports: [], consoleErrors: [], pageErrors: [] };

for (const vp of viewports) {
  const context = await browser.newContext({
    viewport: { width: vp.width, height: vp.height },
    deviceScaleFactor: 1,
  });
  const page = await context.newPage();

  page.on("console", (msg) => {
    if (msg.type() === "error") report.consoleErrors.push(`[${vp.name}] ${msg.text()}`);
  });
  page.on("pageerror", (err) => report.pageErrors.push(`[${vp.name}] ${err.message}`));

  await page.goto(PREVIEW, { waitUntil: "networkidle" });
  await page.waitForTimeout(400);

  const data = await page.evaluate(() => {
    const h1 = document.querySelector("h1");
    const hero = h1?.closest("section")?.querySelector("p");
    const link = document.querySelector('a[download="Ardoise.apk"]');
    const btn = link?.getBoundingClientRect();
    return {
      title: h1?.textContent?.replace(/\s+/g, " ").trim() ?? null,
      paragraph: hero?.textContent?.replace(/\s+/g, " ").trim() ?? null,
      visibleEntityText: document.body.innerText.includes("&nbsp;"),
      htmlLang: document.documentElement.lang,
      crossOriginIsolated: window.crossOriginIsolated === true,
      scrollWidth: document.documentElement.scrollWidth,
      innerWidth: window.innerWidth,
      overflowX:
        document.documentElement.scrollWidth > window.innerWidth + 1,
      downloadHref: link?.getAttribute("href") ?? null,
      downloadAttr: link?.getAttribute("download") ?? null,
      buttonWidth: btn ? Math.round(btn.width) : null,
      buttonHeight: btn ? Math.round(btn.height) : null,
      bodyBackground: getComputedStyle(document.body).backgroundColor,
      h1FontSize: h1 ? getComputedStyle(h1).fontSize : null,
    };
  });

  // Le lien de téléchargement doit vraiment pointer vers l'APK servi.
  const apk = await context.request.get(new URL("/Ardoise.apk", PREVIEW).toString());

  await page.screenshot({ path: `${OUT}/${vp.name}.png`, fullPage: true });

  report.viewports.push({
    ...vp,
    ...data,
    apkStatus: apk.status(),
    apkBytes: Number(apk.headers()["content-length"] ?? 0),
    titleMatches: data.title === EXPECTED.title,
    paragraphMatches: data.paragraph === EXPECTED.paragraph,
    entityLeak: data.visibleEntityText,
  });

  await context.close();
}

await browser.close();
console.log(JSON.stringify(report, null, 2));