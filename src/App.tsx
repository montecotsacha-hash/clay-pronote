import type { CSSProperties } from "react";

const APK_URL = "/Ardoise.apk";
const APK_NAME = "Ardoise.apk";

/** Stagger helper: each block fades in slightly after the previous one. */
const delay = (ms: number): CSSProperties => ({ "--d": `${ms}ms` }) as CSSProperties;

const features = [
  {
    title: "Hors ligne d'abord",
    body: "Consulte tes dernières notes même sans réseau. Si l'option est activée, Ardoise tente une synchronisation périodique lorsque tu es connecté.",
    color: "#6fc7b8",
  },
  {
    title: "Notifications locales",
    body: "Après une synchronisation en arrière-plan, Ardoise peut t'alerter localement si une nouvelle note ou une variation de moyenne est détectée. Android peut demander l'autorisation d'afficher les notifications.",
    color: "#e8c36b",
  },
  {
    title: "Moyennes fiables",
    body: "Retrouve les notes et moyennes visibles dans Pronote. Si l'établissement ne fournit pas de moyenne générale, Ardoise en propose un calcul local à partir des données disponibles.",
    color: "#e59ac4",
  },
  {
    title: "100 % privé",
    body: "Tes identifiants sont envoyés directement au serveur Pronote de ton établissement, sans serveur intermédiaire Ardoise. Le jeton de session est conservé chiffré sur l'appareil.",
    color: "#7fb3e8",
  },
];

const steps = [
  {
    n: "1",
    title: "Télécharge l'APK",
    body: "Télécharge le fichier sur ton téléphone Android depuis le bouton ci-dessous.",
  },
  {
    n: "2",
    title: "Autorise l'installation",
    body: "Android affiche « installer une application inconnue » : accepte pour ton navigateur, une seule fois.",
  },
  {
    n: "3",
    title: "Connecte-toi",
    body: "Saisis l'adresse Pronote de ton établissement, puis tes propres identifiants élève. Les accès via un ENT ne sont pas pris en charge.",
  },
];

export default function App() {
  return (
    <div className="min-h-screen" translate="no">
      {/* En-tête */}
      <header
        className="reveal mx-auto flex max-w-5xl items-center justify-between px-5 py-6"
        style={delay(0)}
      >
        <div className="flex items-center gap-3">
          <span className="clay-dot" aria-hidden="true" />
          <span className="text-xl font-extrabold tracking-tight">Ardoise</span>
        </div>
        <a
          href={APK_URL}
          download={APK_NAME}
          className="clay-btn !px-5 !py-2.5 text-sm"
        >
          Télécharger
        </a>
      </header>

      {/* Accroche */}
      <section
        className="reveal mx-auto max-w-5xl px-5 pt-10 pb-16 text-center sm:pt-16"
        style={delay(80)}
      >
        <span className="clay-chip">Version 1.2.3 · Android 7.0 et plus · environ 12 Mo</span>
        <h1 className="mx-auto mt-6 max-w-2xl text-4xl font-black leading-tight tracking-tight sm:text-6xl">
          Tes notes, enfin{" "}
          <span className="text-[#9b7be0]">lisibles</span>.
        </h1>
        <p className="mx-auto mt-5 max-w-xl text-lg text-[#6b5f87]">
          <strong className="font-extrabold">Ardoise</strong> est l'application
          mobile qui suit tes notes et ta moyenne Pronote — avec un accès hors
          ligne à tes dernières données et une connexion directe au serveur de
          ton établissement.
        </p>
        <div className="mt-9 flex flex-col items-center justify-center gap-4 sm:flex-row">
          <a
            href={APK_URL}
            download={APK_NAME}
            className="clay-btn w-full sm:w-auto"
          >
            ⬇️ Télécharger {APK_NAME}
          </a>
          <a href="#installation" className="clay-btn clay-btn--soft w-full sm:w-auto">
            Voir l'installation
          </a>
        </div>
        <p className="mt-4 text-sm text-[#6b5f87]">
          APK signé · environ 12 Mo · aucun serveur intermédiaire Ardoise
        </p>
      </section>

      {/* Fonctionnalités */}
      <section className="mx-auto max-w-5xl px-5 pb-16" aria-labelledby="fonctions">
        <h2 id="fonctions" className="sr-only">
          Ce que fait l'application
        </h2>
        <div className="grid gap-5 sm:grid-cols-2">
          {features.map((feature, index) => (
            <div
              key={feature.title}
              className="clay-card clay-card--lift reveal p-6"
              style={delay(160 + index * 90)}
            >
              <div className="flex items-center gap-3">
                <span
                  className="accent-dot"
                  style={{ background: feature.color }}
                  aria-hidden="true"
                />
                <h3 className="text-lg font-extrabold">{feature.title}</h3>
              </div>
              <p className="mt-2 text-[15px] leading-relaxed text-[#6b5f87]">
                {feature.body}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* Installation */}
      <section
        id="installation"
        className="mx-auto max-w-5xl scroll-mt-8 px-5 pb-16"
        aria-labelledby="installation-titre"
      >
        <div className="clay-card reveal p-7 sm:p-10" style={delay(200)}>
          <h2
            id="installation-titre"
            className="text-2xl font-black tracking-tight sm:text-3xl"
          >
            Installation en trois étapes
          </h2>
          <div className="mt-8 grid gap-7 sm:grid-cols-3">
            {steps.map((step, index) => (
              <div key={step.n} className="reveal" style={delay(260 + index * 90)}>
                <div className="flex items-center gap-3">
                  <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[#9b7be0] text-base font-black text-white shadow-md">
                    {step.n}
                  </span>
                  <h3 className="font-extrabold">{step.title}</h3>
                </div>
                <p className="mt-3 text-[15px] leading-relaxed text-[#6b5f87]">
                  {step.body}
                </p>
              </div>
            ))}
          </div>
          <div className="mt-9 flex justify-center">
            <a href={APK_URL} download={APK_NAME} className="clay-btn">
              ⬇️ Télécharger maintenant
            </a>
          </div>
          <p className="mt-6 text-center text-sm text-[#6b5f87]">
            L'application nécessite Android 7.0 (Nougat) ou une version plus
            récente. Comme pour toute installation hors Play Store, Android te
            demandera d'autoriser l'installation depuis ton navigateur. Ardoise
            se connecte directement au serveur Pronote de ton établissement ;
            les accès via un ENT ne sont pas pris en charge.
          </p>
        </div>
      </section>

      {/* Pied de page */}
      <footer className="mx-auto max-w-5xl px-5 pb-12 text-center text-sm text-[#6b5f87]">
        <div className="clay-card mx-auto max-w-3xl p-6">
          <p className="font-semibold">Ardoise — application non officielle</p>
          <p className="mt-2 leading-relaxed">
            Application indépendante, non affiliée à Index-Éducation. « Pronote »
            est une marque de son éditeur ; Ardoise est un lecteur indépendant.
            Tes identifiants sont transmis directement au serveur Pronote de
            ton établissement pour la connexion ; le jeton de session est
            chiffré sur ton appareil. Aucun serveur intermédiaire Ardoise.
          </p>
        </div>
        <p className="mt-6">Fait avec 💜 pour les lycéens</p>
      </footer>
    </div>
  );
}