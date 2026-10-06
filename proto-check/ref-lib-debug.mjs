import * as pawnote from "pawnote";

/** Fetcher compatible @literate.ink/utilities mais instrumenté. */
const debugFetcher = async (options) => {
  const url = typeof options.url === "string" ? options.url : options.url.href;
  console.log("→", options.method ?? "GET", url);
  if (options.headers) console.log("  headers:", JSON.stringify(options.headers));
  const init = {
    method: options.method ?? "GET",
    redirect: options.redirect ?? "follow",
    headers: options.headers ?? {},
  };
  if (options.content !== undefined) init.body = options.content;
  const response = await fetch(url, init);
  const content = await response.text();
  console.log("←", response.status, "| type:", response.type, "| taille:", content.length, "| redirected:", response.redirected, "| url finale:", response.url);
  console.log("  début:", JSON.stringify(content.slice(0, 150)));
  return { status: response.status, content, headers: response.headers };
};

try {
  const session = pawnote.createSessionHandle(debugFetcher);
  const auth = await pawnote.loginCredentials(session, {
    url: "https://demo.index-education.net/pronote/eleve.html",
    kind: "student",
    username: "FAUXCOMPTE",
    password: "MauvaisMotDePasse",
    deviceUUID: "11111111-2222-4333-8444-555555555555",
  });
  console.log("Connexion réussie :", auth.username);
} catch (error) {
  console.log("Erreur capturée :", error.name, "—", error.message);
}
