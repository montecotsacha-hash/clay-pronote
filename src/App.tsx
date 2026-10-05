import type { CSSProperties } from "react";

const APK_URL = "/Ardoise.apk";
const APK_NAME = "Ardoise.apk";

/** Stagger helper: each block fades in slightly after the previous one. */
const delay = (ms: number): CSSProperties => ({ "--d": `${ms}ms` }) as CSSProperties;

const features = [
  {
    title: "Hors ligne d'abord",
    body: "Tes notes restent lisibles sans réseau. L'application synchronise en arrière-plan dès que la connexion revient.",
    color: "#6fc7b8",
  },
  {
    title: "Notifications locales",
    body: "Une nouvelle note ou une moyenne qui bouge : l'alerte est générée sur ton téléphone, pas sur un serveur.",
    color: "#e8c36b",
  },
  {
    title: "Moyennes fiables",
    body: "Coefficients, notes sur 10 et «&nbsp;non significatif&nbsp;»&nbsp;: tout est calculé correctement, et la moyenne cachée est reconstruite.",
    color: "#e59ac4",
  },
  {
    title: "100&nbsp;% privé",
    body: "Identifiants chiffrés par le Keystore Android. Aucune donnée scolaire ne transite par un tiers.",
    color: "#7fb3e8",
  },
];

const steps = [
  {
    n: "1",
    title: "Télécharge l'APK",
    body: "Touche le bouton de téléchargement depuis ton téléphone Android.",
  },
  {
    n: "2",
    title: "Autorise l'installation",
    body: "Android affiche «&nbsp;installer une application inconnue&nbsp;»&nbsp;: accepte pour ton navigateur, une seule fois.",
  },
  {
    n: "3",
    title: "Connecte-toi",
    body: "Les identifiants de démonstration sont préremplis&nbsp;: un appui sur «&nbsp;Se connecter&nbsp;» et c'est parti.",
  },
];

export default function App() {
  return (
    <div className="min-h-screen">
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
        <span className="clay-chip">Version 1.0.0 · Android 7.0 et plus · 18 Mo</span>
        <h1 className="mx-auto mt-6 max-w-2xl text-4xl font-black leading-tight tracking-tight sm:text-6xl">
          Tes notes, enfin{" "}
          <span className="text-[#9b7be0]">lisibles</span>.
        </h1>
        <p className="mx-auto mt-5 max-w-xl text-lg text-[#6b5f87]">
          <strong className="font-extrabold">Ardoise</strong> est l'application
          mobile qui suit tes notes et ta moyenne Pronote — hors ligne, en
          notifications locales, sans jamais envoyer tes identifiants à un
          serveur.
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
          Fichier signé · environ 18 Mo · aucune donnée collectée
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
            récente. Le fichier est signé avec une clé de débogage : Android
            affiche donc un avertissement de sécurité, ce qui est normal pour
            une application distribuée hors magasin.
          </p>
        </div>
      </section>

      {/* Pied de page */}
      <footer className="mx-auto max-w-5xl px-5 pb-12 text-center text-sm text-[#6b5f87]">
        <div className="clay-card mx-auto max-w-3xl p-6">
          <p className="font-semibold">Ardoise — application non officielle</p>
          <p className="mt-2 leading-relaxed">
            Application indépendante, non affiliée à Index-Éducation. «&nbsp;Pronote&nbsp;»
            est une marque de son éditeur&nbsp;; Ardoise est un lecteur indépendant.
            Tes identifiants restent chiffrés sur ton appareil : aucun compte,
            aucun serveur intermédiaire.
          </p>
        </div>
        <p className="mt-6">Fait avec 💜 pour les lycéens</p>
      </footer>
    </div>
  );
}