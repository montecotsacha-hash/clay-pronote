# Publier Ardoise gratuitement

État au 16 octobre 2026. Toutes les affirmations sur les frais des magasins sont
sourcées en fin de document.

## Réponse courte

| Voie                           | Coût                                                                                  | Public visé                                        |
| ------------------------------ | ------------------------------------------------------------------------------------- | -------------------------------------------------- |
| Google Play (compte personnel) | **25 $ une fois, obligatoire**                                                        | tous les Android — mais pas gratuit                |
| GitHub Releases + Obtainium    | **0 €**                                                                               | tous les Android (installation directe + màj auto) |
| Uptodown                       | **0 €**                                                                               | tous les Android, catalogue public                 |
| APKPure                        | **0 €**                                                                               | tous les Android, catalogue public                 |
| Huawei AppGallery              | **0 €**                                                                               | Android hors Play Store, selon pays                |
| Samsung Galaxy Store           | **0 €** côté magasin                                                                  | téléphones Samsung                                 |
| Aptoide Connect                | **0 €** seulement si l'app est gratuite et sans achat intégré                         | tous les Android                                   |
| F-Droid / IzzyOnDroid          | 0 € — **bloqué** : licence libre exigée                                               | utilisateurs de logiciel libre                     |
| Amazon Appstore                | gratuit — **inutilisable** : plus d'apps pour téléphones Android depuis le 20/08/2025 | Fire TV / Fire Tablet                              |

**Le Play Store n'est pas gratuit et ne peut pas le devenir.** Google facture
25 $ une seule fois à la création du compte, sans dispense étudiante,
associative ou régionale documentée. Les 25 $ sont par compte, pas par
application.

Trois façons de contourner le paiement pour Play existent, mais aucune ne donne
un compte Play public personnel gratuit :

1. **Être invité sur un compte Play existant** (celui d'un établissement, d'une
   association, d'un proche) : les utilisateurs invités ne paient pas les 25 $.
   C'est gratuit, mais tu publies avec le compte de quelqu'un d'autre.
2. **Compte « distribution limitée » gratuit** du nouveau Android Developer
   Console : gratuit et sans pièce d'identité, mais l'app ne peut être installée
   que sur **20 appareils explicitement autorisés**. Ce n'est pas une
   publication publique.
3. **Payer les 25 $** (facultatif : la distribution gratuite ci-dessous est
   suffisante pour installer l'app sur n'importe quel téléphone).

## Ce qui est déjà prêt dans le dépôt

| Élément                     | Valeur                                                                                     |
| --------------------------- | ------------------------------------------------------------------------------------------ |
| `applicationId`             | `com.pronote.clay`                                                                         |
| Version                     | 1.6.0 « Cadran » (versionCode 13)                                                          |
| Android minimum / cible     | API 24 (7.0) / API 35                                                                      |
| APK signé (site + releases) | `Ardoise.apk` — SHA-256 `071a2ea6afab76ee126afa5b0ac5e85520aa0445d6dcd5144cd338988d3766f6` |
| Bundle pour magasins        | `Ardoise.aab` — SHA-256 `f0aa8c466ef3ec744f59fe1ad4aff329d2e8dc1f8fd3f962ed818522d3afa082` |
| Clé d'upload                | `android/app/upload-keystore.jks` (RSA 4096, valable 30 ans, créée le 14/11/2026), hors git |
| Mots de passe de la clé     | `android/app/keystore.properties`, hors git                                                |
| Captures d'écran            | `screenshots/` (page d'accueil : captures Play de l'app à produire)                        |
| Page de téléchargement      | <https://ardoise.freebuff.page>                                                            |

Note : la page publique sert déjà cette version — `/version.json` annonce `versionCode` 12
et l'empreinte du fichier téléchargé est identique à celle de la release.

## Nommage des versions

Chaque version reçoit un **nom propre, jamais réutilisé** (voir `CHANGELOG.md`) :
`1.5.0` = « Boussole », `1.4.0` = « Écho ». Le nom est affiché sur le site, dans
le titre de la release et dans le journal, donc un changement de nom signale un
changement de version. La liste des noms à venir est en bas de `CHANGELOG.md` :
on prend le premier de la liste et on l'y retire.

Deux contrôles automatiques refusent une version mal nommée :
`src/version.test.ts` (noms uniques, même version dans le journal, le site et
Gradle) et `.github/workflows/privacy.yml` (aucun secret ni donnée personnelle).

⚠️ **Sauvegarde `upload-keystore.jks` + `keystore.properties` maintenant.** Cette
clé est la seule à pouvoir mettre à jour une app déjà installée. Si elle est
perdue, tous les utilisateurs existants doivent désinstaller puis réinstaller.
Elle ne doit jamais être commitée : c'est déjà le cas (`.gitignore`).

## Voie 1 — GitHub Releases (gratuit, automatique)

Le workflow `.github/workflows/android-release.yml` construit, signe, vérifie et
publie l'APK et l'AAB à chaque tag `v*`, via les runners GitHub gratuits
(illimités sur un dépôt public, 2 000 min/mois sur un dépôt privé).

Mise en place, une seule fois :

```bash
# 1. Secrets du dépôt (Settings → Secrets and variables → Actions) :
#    ANDROID_KEYSTORE_BASE64    = base64 -w0 android/app/upload-keystore.jks
#    ANDROID_KEYSTORE_PASSWORD  = storePassword de keystore.properties
#    ANDROID_KEY_ALIAS          = keyAlias de keystore.properties
#    ANDROID_KEY_PASSWORD       = keyPassword de keystore.properties

# 2. local.properties ne doit plus être suivi (chemin de dev en dur) :
git rm --cached android/local.properties

# 3. Publier une version :
git tag v1.5.0 && git push origin v1.5.0
```

Côté utilisateur, la mise à jour automatique se fait avec **Obtainium** : ajouter
l'URL des releases (`https://github.com/montecotsacha-hash/ardoise/releases`)
dans l'app, qui suit ensuite chaque nouvelle version sans magasin.

## Changement de clé de signature à la 1.6.0 (14 novembre 2026)

La clé utilisée jusqu'à la 1.5.0 a été perdue (plus aucun `upload-keystore.jks` sur
le poste de développement, absent aussi de l'historique git). La 1.6.0 est donc
signée avec une **nouvelle clé d'upload** `CN=Ardoise` (RSA 4096, empreinte
SHA-256 du certificat `c1c433d6a19ee91958d1aa694b0fd6c19a0e2bfbb1eb2798c7f7669f1dcf4d95`).

Conséquence à annoncer clairement aux utilisateurs : Android refuse une mise à
jour quand la signature change. Une installation en 1.5.0 ou antérieure doit être
**désinstallée puis réinstallée** une fois en 1.6.0 (les données locales de
l'application sont perdues, elles se retéléchargent à la reconnexion). Toutes les
mises à jour suivantes seront de nouveau transparentes, à condition de conserver
cette nouvelle clé : **sauvegarde `upload-keystore.jks` + `keystore.properties`
maintenant**.

## Cohérence de signature avant la 1.6.0

Tous les canaux partagent désormais **un seul signataire : `CN=Ardoise`, la
clé d'upload** :

- site production et preview, sur `/Ardoise.apk` **et** `/Pronote.apk` —
  SHA-256 `56aa7fdb66dcfeb2a425b99b9cbcc01be921c10eed9e9f18869b3c16fab2794e`,
  vérifié par téléchargement complet des deux environnements ;
- `Ardoise.aab` et les futures releases GitHub : même clé.

Seule conséquence à connaître : une installation faite **avant** cette bascule,
avec l'ancien APK signé à la clé de debug Android (`CN=Android Debug`), ne peut
pas se mettre à jour automatiquement — Android refuse un changement de
signature. Il faut désinstaller puis réinstaller **une fois** ; ensuite toutes
les mises à jour passent sur tous les canaux. L'ancien binaire est conservé en
sauvegarde hors dépôt si l'état antérieur devait être restauré.

## Voie 2 — Magasins gratuits (compte à créer par toi)

Pour chacune : créer un compte développeur, puis téléverser `Ardoise.aab` (ou
l'APK quand le magasin ne prend pas les bundles) et remplir la fiche.

- **Uptodown** — inscription gratuite, pas de SDK obligatoire, pas de partage de
  revenus. Console développeurs : <https://en.uptodown.com/developers-console>
- **APKPure** — publication et mises à jour gratuites, revue manuelle de chaque
  APK : <https://apkpure.com/developer.html>
- **Huawei AppGallery** — vérification du HUAWEI ID, puis soumission ; pas de
  frais d'inscription, annuels ou de soumission :
  <https://developer.huawei.com/consumer/en/appgallery/>
- **Samsung Galaxy Store** — 0 € d'inscription et de publication, mais statut de
  vendeur commercial exigé, même pour une app gratuite :
  <https://developer.samsung.com/galaxy-store/prepare.html>
- **Aptoide** — gratuit uniquement par la voie automatique (app gratuite, déjà
  sur Play, sans achat intégré) : <https://docs.connect.aptoide.com/docs/app-submission-faqs>

Éléments attendus par ces magasins, à préparer une fois : captures d'écran de
l'application (pas de la page web), description courte et longue en français, et
**une URL publique de politique de confidentialité** (voir ci-dessous).

## Voie 3 — Google Play à 25 $ (si tu changes d'avis)

Tout le technique est déjà fait : bundle signé, `targetSdk 35`, clé d'upload
RSA 4096. Une fois le compte créé, il reste à créer l'app, activer Play App
Signing, envoyer `Ardoise.aab`, puis remplir la fiche (captures, description),
la classification du contenu, le formulaire « sécurité des données » et l'URL de
politique de confidentialité.

Deux règles Play à connaître avant de payer :

- Un compte personnel créé après le 13 novembre 2023 doit passer par un **test
  fermé avec au moins 12 testeurs pendant 14 jours** avant d'obtenir l'accès à la
  production.
- « Pronote » est une marque d'Index-Éducation et Ardoise est un client non
  officiel : la fiche doit le dire clairement (c'est déjà le cas sur le site), et
  la revue Play peut malgré tout refuser une app qui se connecte au service d'un
  tiers. C'est le risque principal, indépendant du prix.

## F-Droid et IzzyOnDroid : gratuits mais bloqués

Ces deux dépôts ne coûtent rien, mais exigent **une application libre et son code
source accessible** : le dépôt n'a aucune licence aujourd'hui. Il faudrait aussi
accepter qu'F-Droid signale « service réseau non libre » — l'app dépend du
service propriétaire Pronote de l'établissement. Tant que la question de la
licence n'est pas tranchée, ces voies restent fermées.

## Politique de confidentialité

Tous les magasins, y compris Play, demandent une URL publique. Le texte est
prêt : `PRIVACY.md`. Il reste à le publier (site, GitHub Pages…) et à coller
l'URL dans chaque fiche.

## Vérification d'identité Google pour l'installation hors Play

Depuis le 30 septembre 2026, Google déploie dans certains pays une vérification
du développeur pour les apps installées **hors** Play Store (extension mondiale
prévue en 2027). Elle concerne donc aussi les voies 1 et 2. Compte gratuit
« distribution limitée » (20 appareils) ou compte complet (25 $) ; les
développeurs Play existants passent par Play Console. À surveiller pour la
France.

## Sources

- Frais du compte Play et test à 12 testeurs :
  <https://support.google.com/googleplay/android-developer/answer/6112435> ·
  <https://support.google.com/googleplay/android-developer/answer/14151465>
- Inviter des utilisateurs sur un compte Play (gratuit pour l'invité) :
  <https://support.google.com/googleplay/android-developer/answer/9844686>
- Compte gratuit « distribution limitée » (20 appareils) :
  <https://support.google.com/android-developer-console/answer/16561738> ·
  <https://developer.android.com/developer-verification/guides/limited-distribution>
- Vérification des apps hors Play : <https://developer.android.com/developer-verification>
- Uptodown : <https://en.uptodown.com/developers-console> —
  APKPure : <https://apkpure.com/developer.html> —
  Huawei : <https://developer.huawei.com/consumer/en/appgallery/> —
  Samsung : <https://developer.samsung.com/galaxy-store/faq.html> —
  Aptoide : <https://docs.connect.aptoide.com/docs/app-submission-faqs>
- Fin de l'Appstore Amazon pour téléphones Android :
  <https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program>
- F-Droid, critères d'inclusion :
  <https://f-droid.org/en/docs/Inclusion_Policy/> —
  IzzyOnDroid : <https://izzyondroid.org/docs/general/AppInclusionPolicy/> —
  Obtainium : <https://github.com/ImranR98/Obtainium>
