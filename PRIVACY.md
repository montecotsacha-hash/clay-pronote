# Politique de confidentialité — Ardoise

Dernière mise à jour : 18 octobre 2026.

Ardoise est une application Android indépendante, non affiliée à Index-Éducation.
Elle permet de consulter les notes, moyennes et l'emploi du temps d'un compte
Pronote. Ce document décrit exactement les données traitées par l'application ;
il est rédigé à partir de son code source.

## Aucune donnée ne passe par un serveur Ardoise

Ardoise n'exploite **aucun serveur applicatif**. Il n'y a ni compte Ardoise, ni
collecte, ni télémétrie, ni publicité, ni SDK analytique ou publicitaire dans
l'application. Le site de distribution du projet sert uniquement la page de
téléchargement, l'APK et un fichier `version.json` public ; il ne reçoit aucune
donnée d'utilisateur.

## Données transmises

- **Adresse de l'établissement et identifiants Pronote** : transmis uniquement au
  serveur Pronote de l'établissement saisi par l'utilisateur, pour ouvrir la
  session. C'est le seul destinataire.
- **Données scolaires** (notes, moyennes, emploi du temps, informations de
  l'élève renvoyées par Pronote) : récupérées depuis ce même serveur pour
  l'affichage dans l'application.
- **Vérification de mise à jour** : au lancement, l'application interroge le
  site de distribution (`version.json`, un fichier public) pour savoir si une
  version plus récente existe. Cette requête ne contient ni identifiant ni
  donnée scolaire — au même titre qu'une visite ordinaire du site. La vérification
  se lance aussi à la main dans les paramètres.

L'éditeur d'Ardoise ne reçoit aucune de ces données et n'y a aucun accès. Les
accès via un ENT ne sont pas pris en charge par l'application.

## Données stockées sur l'appareil

- **Identifiant, mot de passe et jeton de session Pronote** : conservés
  chiffrés sur l'appareil via le magasin de clés Android (Android Keystore,
  `EncryptedSharedPreferences`, AES-256). Ils ne sont jamais écrits en clair,
  jamais journalisés, et sont effacés par « Se déconnecter » ou « Supprimer mes
  données » dans les paramètres de l'application.
- **Cache des données scolaires** : conservé localement pour l'affichage hors
  ligne.

Rien n'est sauvegardé dans le cloud : les sauvegardes Android sont désactivées
(`allowBackup="false"`), donc ces données ne quittent pas l'appareil.

## Autorisations demandées

| Autorisation           | Usage                                                                                                         |
| ---------------------- | ------------------------------------------------------------------------------------------------------------- |
| `INTERNET`             | joindre le serveur Pronote de l'établissement et le site de distribution (vérification de mise à jour)        |
| `ACCESS_NETWORK_STATE` | savoir si l'appareil est connecté (mode hors ligne, synchronisation)                                          |
| `POST_NOTIFICATIONS`   | notifications **locales** après une synchronisation (optionnel)                                               |
| `USE_BIOMETRIC`        | verrouillage de l'application (optionnel : empreinte, visage ou code de l'appareil) — aucune donnée transmise |

## Enfants et données scolaires

L'application n'affiche que les données du compte Pronote dont l'utilisateur
saisit lui-même les identifiants. Aucune donnée n'est partagée avec un tiers,
revendue ou exploitée à d'autres fins.

## Copies, suppression, questions

« Supprimer mes données » dans les paramètres de l'application efface
définitivement les identifiants et le cache de l'appareil. Pour toute question ou
demande relative à ces données, ouvrir un ticket sur le dépôt du projet :
<https://github.com/Martineleve-hash/clay-pronote/issues>.

## Modifications

Toute évolution de cette politique sera publiée à cette adresse, avec sa date de
mise à jour.
