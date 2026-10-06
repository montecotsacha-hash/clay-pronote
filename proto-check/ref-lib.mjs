import * as pawnote from "pawnote";

console.log("AccountKind =", JSON.stringify(pawnote.AccountKind));
try {
  const session = pawnote.createSessionHandle();
  const auth = await pawnote.loginCredentials(session, {
    url: "https://demo.index-education.net/pronote/eleve.html",
    kind: pawnote.AccountKind.STUDENT,
    username: "FAUXCOMPTE",
    password: "MauvaisMotDePasse",
    deviceUUID: "11111111-2222-4333-8444-555555555555",
  });
  console.log("Connexion réussie (inattendu) :", auth.username);
} catch (error) {
  console.log("Erreur capturée :", error.name, "—", error.message);
  if (error.name === "BadCredentialsError") {
    console.log(">>> BadCredentialsError = le protocole complet fonctionne jusqu'à la vérification du mot de passe");
  }
}
