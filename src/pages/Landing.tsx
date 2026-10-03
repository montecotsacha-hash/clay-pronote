import { motion } from "framer-motion";
import {
  ArrowRight,
  BarChart3,
  BellRing,
  Calculator,
  CloudOff,
  Fingerprint,
  GraduationCap,
  LineChart,
  Lock,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Smartphone,
  WifiOff,
} from "lucide-react";
import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router";
import { ClayButton } from "@/components/clay/ClayButton";
import { ClayPill, ClaySectionTitle, ClaySurface, ClayWell } from "@/components/clay/ClaySurface";
import { useAuth } from "@/hooks/use-auth";

const fadeUp = {
  hidden: { opacity: 0, y: 24 },
  show: { opacity: 1, y: 0 },
};

const SPRING = { duration: 0.55, ease: [0.22, 1, 0.36, 1] as const };

const FEATURES = [
  {
    icon: Calculator,
    title: "Moyenne en un coup d'œil",
    body: "Ta moyenne générale s'affiche dès l'ouverture de ton tableau de bord. Si ton établissement la masque, on la calcule localement à partir de tes notes et de tes coefficients.",
    tint: "var(--primary)",
  },
  {
    icon: LineChart,
    title: "Évolution dans le temps",
    body: "Suis ta progression par semaine, par mois ou par trimestre, matière par matière, avec des graphiques lisibles.",
    tint: "var(--chart-2)",
  },
  {
    icon: CloudOff,
    title: "Fonctionne hors ligne",
    body: "Tes notes et ta moyenne restent consultables sans réseau. L'interface affiche le cache puis se met à jour en silence.",
    tint: "var(--chart-3)",
  },
  {
    icon: BellRing,
    title: "Alertes locales",
    body: "Nouvelle note ou moyenne qui bouge : une notification est générée sur ton appareil, jamais via un serveur tiers.",
    tint: "var(--chart-4)",
  },
  {
    icon: RefreshCw,
    title: "Synchronisation discrète",
    body: "Synchronisation périodique uniquement avec du réseau, sans jamais lancer deux requêtes en parallèle.",
    tint: "var(--chart-5)",
  },
  {
    icon: BarChart3,
    title: "Le détail qui compte",
    body: "Chaque note avec sa date, son coefficient et son commentaire. Filtre par matière, par jour ou par période.",
    tint: "var(--primary)",
  },
];

const SECURITY = [
  {
    icon: Lock,
    title: "Identifiants chiffrés",
    body: "Tes identifiants sont chiffrés en AES-GCM avec une clé non exportable stockée dans le magasin sécurisé de l'appareil. Jamais en clair, jamais dans un journal.",
  },
  {
    icon: ShieldCheck,
    title: "Aucun serveur à nous",
    body: "Pas de backend propriétaire, pas de compte chez nous, pas de traçage. Les données scolaires restent sur ton téléphone.",
  },
  {
    icon: Fingerprint,
    title: "Verrouillage biométrique",
    body: "Protège l'application avec ton empreinte, ta reconnaissance faciale ou le code de ton appareil.",
  },
];

const STEPS = [
  {
    number: "01",
    title: "Crée ton compte",
    body: "Inscris-toi en quelques secondes et choisis la formule qui te convient. L'essai est gratuit.",
  },
  {
    number: "02",
    title: "Connecte ton établissement",
    body: "Indique l'adresse Pronote de ton collège ou lycée, puis tes identifiants. Ils restent chiffrés sur ton appareil.",
  },
  {
    number: "03",
    title: "Ouvre ton tableau de bord",
    body: "Ta moyenne apparaît immédiatement, puis se met à jour toute seule en arrière-plan.",
  },
];

const PLANS = [
  {
    name: "Découverte",
    price: "0 €",
    cadence: "pour toujours",
    description: "Pour essayer et voir si l'espace te convient.",
    features: [
      "Moyenne générale automatique",
      "Jusqu'à 2 matières suivies",
      "Consultation hors ligne",
    ],
    cta: "Commencer gratuitement",
    highlighted: false,
  },
  {
    name: "Essentiel",
    price: "2,99 €",
    cadence: "par mois",
    description: "Le suivi complet, pour un élève.",
    features: [
      "Toutes les matières et toutes les notes",
      "Historique et courbes d'évolution",
      "Synchronisation automatique",
      "Notifications de nouvelles notes",
    ],
    cta: "Choisir Essentiel",
    highlighted: true,
  },
  {
    name: "Famille",
    price: "4,99 €",
    cadence: "par mois",
    description: "Plusieurs élèves suivis depuis un même compte.",
    features: [
      "Tout Essentiel inclus",
      "Jusqu'à 4 profils d'élèves",
      "Verrouillage biométrique",
      "Support prioritaire",
    ],
    cta: "Choisir Famille",
    highlighted: false,
  },
];

export default function Landing() {
  const navigate = useNavigate();
  const { hash } = useLocation();
  const { isAuthenticated } = useAuth();

  // Deep links such as "/#tarifs" (used by Settings → Voir les formules) land on
  // the landing page without an automatic scroll, so do it explicitly.
  useEffect(() => {
    if (!hash) return;
    const target = document.getElementById(hash.slice(1));
    if (!target) return;
    const frame = requestAnimationFrame(() =>
      target.scrollIntoView({ behavior: "smooth", block: "start" }),
    );
    return () => cancelAnimationFrame(frame);
  }, [hash]);

  const primaryTarget = isAuthenticated ? "/app" : "/auth";
  const primaryLabel = isAuthenticated ? "Ouvrir mon espace" : "Commencer";

  return (
    <div className="min-h-screen overflow-x-hidden">
      {/* Navbar */}
      <header className="sticky top-0 z-40 px-4 pt-4 sm:px-6">
        <motion.div initial={{ opacity: 0, y: -16 }} animate={{ opacity: 1, y: 0 }} transition={SPRING}>
          <ClaySurface
            depth="md"
            className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3"
          >
            <button
              type="button"
              onClick={() => navigate("/")}
              className="clay-pressable flex items-center gap-3 rounded-2xl pr-2"
            >
              <span
                className="clay-sm flex size-10 items-center justify-center rounded-2xl"
                style={{ backgroundColor: "color-mix(in oklab, var(--primary) 34%, var(--card))" }}
              >
                <GraduationCap className="size-5" aria-hidden />
              </span>
              <span className="text-left">
                <span className="block text-base font-black tracking-tight">Pronote</span>
                <span className="block text-[11px] font-medium text-muted-foreground">
                  Ta moyenne, calculée automatiquement
                </span>
              </span>
            </button>

            <nav className="hidden items-center gap-1 md:flex" aria-label="Sections">
              {[
                { href: "#fonctionnalites", label: "Fonctionnalités" },
                { href: "#comment", label: "Comment ça marche" },
                { href: "#tarifs", label: "Tarifs" },
                { href: "#securite", label: "Sécurité" },
              ].map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  className="clay-pressable rounded-2xl px-3 py-2 text-sm font-semibold text-muted-foreground hover:text-foreground"
                >
                  {link.label}
                </a>
              ))}
            </nav>

            <ClayButton
              size="sm"
              onClick={() => navigate(primaryTarget)}
              icon={<ArrowRight className="size-4" />}
            >
              {primaryLabel}
            </ClayButton>
          </ClaySurface>
        </motion.div>
      </header>

      {/* Hero */}
      <section className="relative px-4 pt-14 sm:px-6 sm:pt-20">
        <div className="mx-auto grid max-w-6xl items-center gap-12 lg:grid-cols-[1.05fr_0.95fr]">
          <motion.div initial="hidden" animate="show" variants={fadeUp} transition={SPRING}>
            <ClayPill tint="var(--accent)" className="mb-5 px-4 py-1.5 text-sm">
              <Sparkles className="size-3.5" aria-hidden />
              Ton espace personnel, prêt en trente secondes
            </ClayPill>

            <h1 className="text-4xl font-black leading-[1.05] tracking-tighter sm:text-6xl">
              Ta moyenne Pronote,
              <br />
              <span
                className="bg-clip-text text-transparent"
                style={{
                  backgroundImage:
                    "linear-gradient(120deg, color-mix(in oklab, var(--primary) 92%, black 8%), color-mix(in oklab, var(--chart-2) 78%, var(--primary) 22%))",
                }}
              >
                calculée automatiquement.
              </span>
            </h1>

            <p className="mt-6 max-w-xl text-lg leading-relaxed text-muted-foreground">
              Connecte ton compte Pronote une seule fois. Ta moyenne générale, tes notes
              et tes matières se mettent à jour tout seuls, dans un espace clair et sans
              distraction.
            </p>

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <ClayButton
                size="lg"
                onClick={() => navigate(primaryTarget)}
                icon={<ArrowRight className="size-5" />}
              >
                {primaryLabel}
              </ClayButton>
              <ClayButton
                size="lg"
                variant="secondary"
                onClick={() => navigate("/app")}
                icon={<Smartphone className="size-5" />}
              >
                Voir une démo
              </ClayButton>
            </div>

            <ul className="mt-8 flex flex-wrap gap-x-6 gap-y-3 text-sm font-medium text-muted-foreground">
              {[
                "Aucune donnée scolaire sur nos serveurs",
                "Fonctionne hors ligne",
                "Moyenne officielle ou calculée",
              ].map((item) => (
                <li key={item} className="flex items-center gap-2">
                  <span
                    aria-hidden
                    className="clay-sm size-2.5 rounded-full"
                    style={{ backgroundColor: "color-mix(in oklab, var(--accent) 80%, var(--card))" }}
                  />
                  {item}
                </li>
              ))}
            </ul>
          </motion.div>

          {/* Clay phone mock-up */}
          <motion.div
            initial={{ opacity: 0, scale: 0.94, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            transition={{ ...SPRING, delay: 0.12 }}
            className="relative"
          >
            <div
              aria-hidden
              className="animate-clay-float absolute -left-6 top-10 size-24 rounded-full opacity-70"
              style={{ backgroundColor: "color-mix(in oklab, var(--chart-4) 45%, transparent)" }}
            />
            <div
              aria-hidden
              className="absolute -right-4 bottom-16 size-20 rounded-full opacity-60"
              style={{ backgroundColor: "color-mix(in oklab, var(--accent) 55%, transparent)" }}
            />

            <ClaySurface depth="lg" className="relative mx-auto w-full max-w-sm p-5">
              <div className="mb-4 flex items-center justify-between">
                <div>
                  <p className="text-xs font-medium text-muted-foreground">Bonjour,</p>
                  <p className="text-base font-bold tracking-tight">Alex</p>
                </div>
                <ClayPill tint="var(--chart-2)">
                  <RefreshCw className="size-3" aria-hidden /> à jour
                </ClayPill>
              </div>

              <ClaySurface
                depth="sm"
                tint="var(--primary)"
                className="p-5"
              >
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                  Moyenne générale
                </p>
                <div className="mt-2 flex items-end gap-2">
                  <span className="text-5xl font-black leading-none tracking-tighter tabular-nums">
                    15,8
                  </span>
                  <span className="pb-1 text-lg font-bold text-muted-foreground">/20</span>
                </div>
                <p className="mt-3 text-xs text-muted-foreground">
                  Mis à jour il y a 2 min · 2e trimestre
                </p>
              </ClaySurface>

              <div className="mt-4 space-y-2.5">
                {[
                  { name: "Mathématiques", value: "16,4", tint: "var(--chart-1)" },
                  { name: "Français", value: "14,8", tint: "var(--chart-4)" },
                  { name: "Anglais", value: "17,2", tint: "var(--chart-2)" },
                ].map((row) => (
                  <ClaySurface key={row.name} depth="sm" className="flex items-center gap-3 px-4 py-3">
                    <span
                      aria-hidden
                      className="size-3 shrink-0 rounded-full"
                      style={{ backgroundColor: `color-mix(in oklab, ${row.tint} 80%, var(--card))` }}
                    />
                    <span className="min-w-0 flex-1 truncate text-sm font-semibold tracking-tight">
                      {row.name}
                    </span>
                    <span className="text-sm font-bold tabular-nums">{row.value}</span>
                  </ClaySurface>
                ))}
              </div>

              <ClayWell className="mt-4 flex items-center gap-3 px-4 py-3">
                <WifiOff className="size-4 text-muted-foreground" aria-hidden />
                <span className="text-xs font-medium text-muted-foreground">
                  Mode hors connexion : dernières données affichées
                </span>
              </ClayWell>
            </ClaySurface>
          </motion.div>
        </div>
      </section>

      {/* Features */}
      <section id="fonctionnalites" className="px-4 pt-24 sm:px-6">
        <div className="mx-auto max-w-6xl">
          <ClaySectionTitle className="mb-8">Tout ce qu'il faut, rien de plus</ClaySectionTitle>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((feature, index) => (
              <motion.div
                key={feature.title}
                initial="hidden"
                whileInView="show"
                viewport={{ once: true, margin: "-80px" }}
                variants={fadeUp}
                transition={{ ...SPRING, delay: index * 0.05 }}
              >
                <ClaySurface depth="md" pressable tint={feature.tint} className="h-full p-6">
                  <span
                    className="clay-sm flex size-12 items-center justify-center rounded-2xl"
                    style={{
                      backgroundColor: `color-mix(in oklab, ${feature.tint} 30%, var(--card))`,
                    }}
                  >
                    <feature.icon className="size-6" aria-hidden />
                  </span>
                  <h3 className="mt-4 text-lg font-bold tracking-tight">{feature.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {feature.body}
                  </p>
                </ClaySurface>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* How it works */}
      <section id="comment" className="px-4 pt-24 sm:px-6">
        <div className="mx-auto max-w-6xl">
          <ClaySectionTitle className="mb-8">Trois étapes, trente secondes</ClaySectionTitle>
          <div className="grid gap-5 md:grid-cols-3">
            {STEPS.map((step, index) => (
              <motion.div
                key={step.number}
                initial="hidden"
                whileInView="show"
                viewport={{ once: true, margin: "-80px" }}
                variants={fadeUp}
                transition={{ ...SPRING, delay: index * 0.08 }}
              >
                <ClaySurface depth="lg" className="h-full p-7">
                  <span
                    className="clay-inset inline-flex size-14 items-center justify-center rounded-3xl text-xl font-black tabular-nums"
                    aria-hidden
                  >
                    {step.number}
                  </span>
                  <h3 className="mt-5 text-xl font-bold tracking-tight">{step.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{step.body}</p>
                </ClaySurface>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* Pricing */}
      <section id="tarifs" className="px-4 pt-24 sm:px-6">
        <div className="mx-auto max-w-6xl">
          <ClaySectionTitle className="mb-8">Des formules simples, sans surprise</ClaySectionTitle>
          <div className="grid gap-5 lg:grid-cols-3">
            {PLANS.map((plan, index) => (
              <motion.div
                key={plan.name}
                initial="hidden"
                whileInView="show"
                viewport={{ once: true, margin: "-80px" }}
                variants={fadeUp}
                transition={{ ...SPRING, delay: index * 0.06 }}
              >
                <ClaySurface
                  depth={plan.highlighted ? "lg" : "md"}
                  pressable
                  tint={plan.highlighted ? "var(--primary)" : undefined}
                  className="flex h-full flex-col p-7"
                >
                  <div className="flex items-start justify-between gap-3">
                    <h3 className="text-lg font-black tracking-tight">{plan.name}</h3>
                    {plan.highlighted ? (
                      <ClayPill tint="var(--primary)">Le plus choisi</ClayPill>
                    ) : null}
                  </div>

                  <p className="mt-4 flex items-end gap-2">
                    <span className="text-4xl font-black tracking-tighter tabular-nums">
                      {plan.price}
                    </span>
                    <span className="pb-1.5 text-xs font-medium text-muted-foreground">
                      {plan.cadence}
                    </span>
                  </p>

                  <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
                    {plan.description}
                  </p>

                  <ul className="mt-6 flex flex-1 flex-col gap-2.5">
                    {plan.features.map((feature) => (
                      <li key={feature} className="flex items-start gap-2.5 text-sm">
                        <span
                          aria-hidden
                          className="clay-sm mt-1 size-2.5 shrink-0 rounded-full"
                          style={{
                            backgroundColor:
                              "color-mix(in oklab, var(--accent) 80%, var(--card))",
                          }}
                        />
                        <span className="leading-relaxed">{feature}</span>
                      </li>
                    ))}
                  </ul>

                  <ClayButton
                    className="mt-7 w-full"
                    variant={plan.highlighted ? "primary" : "secondary"}
                    onClick={() => navigate(primaryTarget)}
                  >
                    {plan.cta}
                  </ClayButton>
                </ClaySurface>
              </motion.div>
            ))}
          </div>

          <p className="mt-6 text-center text-xs text-muted-foreground">
            Sans engagement, résiliable à tout moment. Les paiements sont traités par un
            prestataire certifié — nous ne stockons aucune donnée bancaire.
          </p>
        </div>
      </section>

      {/* Security */}
      <section id="securite" className="px-4 pt-24 sm:px-6">
        <div className="mx-auto max-w-6xl">
          <motion.div
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: "-80px" }}
            variants={fadeUp}
            transition={SPRING}
          >
            <ClaySurface depth="lg" tint="var(--accent)" className="overflow-hidden p-8 sm:p-12">
              <div className="max-w-2xl">
                <ClayPill tint="var(--accent)" className="mb-4">
                  <ShieldCheck className="size-3.5" aria-hidden />
                  Privacy by design
                </ClayPill>
                <h2 className="text-3xl font-black tracking-tighter sm:text-4xl">
                  Tes données restent sur ton téléphone.
                </h2>
                <p className="mt-4 text-base leading-relaxed text-muted-foreground">
                  Nous ne contournons jamais une authentification, un CAPTCHA ou une mesure
                  de sécurité de Pronote, et nous n'explorons que les mécanismes
                  légitimement accessibles. Tes identifiants ne sont jamais envoyés vers un
                  serveur qui nous appartient.
                </p>
              </div>

              <div className="mt-8 grid gap-5 md:grid-cols-3">
                {SECURITY.map((item, index) => (
                  <motion.div
                    key={item.title}
                    initial="hidden"
                    whileInView="show"
                    viewport={{ once: true }}
                    variants={fadeUp}
                    transition={{ ...SPRING, delay: index * 0.06 }}
                  >
                    <ClaySurface depth="sm" className="h-full p-5">
                      <item.icon className="size-6" aria-hidden />
                      <h3 className="mt-3 text-base font-bold tracking-tight">{item.title}</h3>
                      <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                        {item.body}
                      </p>
                    </ClaySurface>
                  </motion.div>
                ))}
              </div>

              <p className="mt-8 rounded-2xl bg-card/60 px-5 py-4 text-xs leading-relaxed text-muted-foreground">
                Tous les établissements n'ont pas la même configuration : adresse
                différente, espace ENT, CAPTCHA ou connexion unique. L'application
                reconnaît ces cas et t'explique clairement quoi faire, au lieu d'échouer
                sans rien dire.
              </p>
            </ClaySurface>
          </motion.div>
        </div>
      </section>

      {/* Final CTA */}
      <section className="px-4 pb-20 pt-24 sm:px-6">
        <motion.div
          initial="hidden"
          whileInView="show"
          viewport={{ once: true }}
          variants={fadeUp}
          transition={SPRING}
          className="mx-auto max-w-3xl text-center"
        >
          <ClaySurface depth="lg" tint="var(--primary)" className="p-10 sm:p-14">
            <h2 className="text-3xl font-black tracking-tighter sm:text-4xl">
              Prêt à voir ta moyenne d'un seul coup d'œil ?
            </h2>
            <p className="mx-auto mt-4 max-w-xl text-base leading-relaxed text-muted-foreground">
              Crée ton compte, connecte ton établissement, et ta moyenne apparaît. Le
              reste suit tout seul.
            </p>
            <div className="mt-8 flex flex-wrap justify-center gap-3">
              <ClayButton
                size="lg"
                onClick={() => navigate(primaryTarget)}
                icon={<ArrowRight className="size-5" />}
              >
                {primaryLabel}
              </ClayButton>
              <ClayButton
                size="lg"
                variant="secondary"
                onClick={() => navigate("/auth")}
                icon={<Lock className="size-5" />}
              >
                Se connecter
              </ClayButton>
            </div>
            <p className="mt-6 text-xs text-muted-foreground">
              Tes identifiants Pronote restent chiffrés sur ton appareil. Toujours.
            </p>
          </ClaySurface>
        </motion.div>
      </section>

      <footer className="px-4 pb-10 sm:px-6">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 text-xs text-muted-foreground sm:flex-row">
          <p>Pronote — ta moyenne, calculée automatiquement.</p>
          <p>
            Non affilié à Index Éducation. Respecte les conditions d'utilisation de
            Pronote.
          </p>
        </div>
      </footer>
    </div>
  );
}
