const APK_URL = "/Ardoise.apk";
const APK_NAME = "Ardoise.apk";

const features = [
  {
    emoji: "📶",
    title: "Hors ligne d'abord",
    body: "Tes notes restent lisibles sans réseau. L'app synchronise en arrière-plan dès que la connexion revient.",
    color: "#6fc7b8",
  },
  {
    emoji: "🔔",
    title: "Notifications locales",
    body: "Nouvelle note ou moyenne qui bouge : l'alerte est générée sur ton téléphone, pas sur un serveur.",
    color: "#e8c36b",
  },
  {
    emoji: "📊",
    title: "Moyennes fiables",
    body: "Coefficients, notes sur 10, « non significatif » : tout est géré, et la moyenne cachée est recalculée.",
    color: "#e59ac4",
  },
  {
    emoji: "🔐",
    title: "100 % privé",
    body: "Identifiants chiffrés par le Keystore Android. Aucune donnée scolaire ne transite par un tiers.",
    color: "#7fb3e8",
  },
];

const steps = [
  {
    n: "1",
    title: "Télécharge l'APK",
    body: "Tape sur le bouton de téléchargement depuis ton téléphone Android.",
  },
  {
    n: "2",
    title: "Autorise l'installation",
    body: "Android demandera « installer une application inconnue » : accepte pour ton navigateur, une seule fois.",
  },
  {
    n: "3",
    title: "Connecte-toi",
    body: "Les identifiants de démo sont préremplis — un tap sur « Se connecter » et c'est parti.",
  },
];

export default function App() {
  return (
    <div className="min-h-screen">
      {/* Header */}
      <header className="mx-auto flex max-w-5xl items-center justify-between px-5 py-6">
        <div className="flex items-center gap-3">
          <span className="clay-dot" />
          <span className="text-xl font-extrabold tracking-tight">Ardoise</span>
        </div>
        <a href={APK_URL} download={APK_NAME} className="clay-btn !px-5 !py-2.5 text-sm">
          Télécharger
        </a>
      </header>

      {/* Hero */}
      <section className="mx-auto max-w-5xl px-5 pt-10 pb-16 text-center sm:pt-16">
        <span className="clay-chip">v1.0.0 · Android 7.0+ · 18 Mo</span>
        <h1 className="mx-auto mt-6 max-w-2xl text-4xl font-black leading-tight tracking-tight sm:text-6xl">
          Tes notes, enfin{" "}
          <span className="text-[#9b7be0]">lisibles</span>.
        </h1>
        <p className="mx-auto mt-5 max-w-xl text-lg text-[#6b5f87]">
          <strong className="font-extrabold">Ardoise</strong> est l'app mobile
          qui suit tes notes et ta moyenne Pronote — hors ligne, en
          notifications locales, sans jamais envoyer tes identifiants à un
          serveur.
        </p>
        <div className="mt-9 flex flex-col items-center justify-center gap-4 sm:flex-row">
          <a href={APK_URL} download={APK_NAME} className="clay-btn w-full sm:w-auto">
            ⬇️ Télécharger {APK_NAME}
          </a>
          <a href="#installer" className="clay-btn clay-btn--soft w-full sm:w-auto">
            Comment l'installer
          </a>
        </div>
        <p className="mt-4 text-sm text-[#6b5f87]">
          Fichier signé, ~18 Mo · aucune donnée collectée
        </p>
      </section>

      {/* Features */}
      <section className="mx-auto max-w-5xl px-5 pb-16">
        <div className="grid gap-5 sm:grid-cols-2">
          {features.map((f) => (
            <div key={f.title} className="clay-card p-6">
              <div className="flex items-center gap-3">
                <span
                  className="accent-dot"
                  style={{ background: f.color }}
                  aria-hidden
                />
                <h3 className="text-lg font-extrabold">{f.title}</h3>
              </div>
              <p className="mt-2 text-[15px] leading-relaxed text-[#6b5f87]">
                {f.body}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* Install steps */}
      <section id="installer" className="mx-auto max-w-5xl px-5 pb-16">
        <div className="clay-card p-7 sm:p-10">
          <h2 className="text-2xl font-black tracking-tight sm:text-3xl">
            Installation en 3 étapes
          </h2>
          <div className="mt-8 grid gap-7 sm:grid-cols-3">
            {steps.map((s) => (
              <div key={s.n}>
                <div className="flex items-center gap-3">
                  <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[#9b7be0] text-base font-black text-white shadow-md">
                    {s.n}
                  </span>
                  <h3 className="font-extrabold">{s.title}</h3>
                </div>
                <p className="mt-3 text-[15px] leading-relaxed text-[#6b5f87]">
                  {s.body}
                </p>
              </div>
            ))}
          </div>
          <div className="mt-9 flex justify-center">
            <a href={APK_URL} download={APK_NAME} className="clay-btn">
              ⬇️ Télécharger maintenant
            </a>
          </div>
        </div>
      </section>

      <footer className="mx-auto max-w-5xl px-5 pb-12 text-center text-sm text-[#6b5f87]">
        <div className="clay-card mx-auto max-w-3xl p-6">
          <p className="font-semibold">Ardoise — application non officielle</p>
          <p className="mt-2 leading-relaxed">
            Application indépendante, non affiliée à Index-Éducation. « Pronote »
            est une marque de son éditeur ; Ardoise est un lecteur indépendant.
            Tes identifiants restent chiffrés sur ton appareil — aucun compte,
            aucun serveur intermédiaire.
          </p>
        </div>
        <p className="mt-6">Fait avec 💜 pour les lycéens</p>
      </footer>
    </div>
  );
}