import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowLeft,
  ArrowRight,
  Building2,
  GraduationCap,
  Link2,
  Loader2,
  Lock,
  School,
  ShieldCheck,
  User,
} from "lucide-react";
import { useCallback, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { ClayButton, ClayIconButton } from "@/components/clay/ClayButton";
import { ClayInput, ClayPasswordField } from "@/components/clay/ClayField";
import { ClayPill, ClaySurface } from "@/components/clay/ClaySurface";
import { useAccountId, usePronote } from "@/lib/pronote/store";
import { useNetworkMonitor } from "@/lib/pronote/network-monitor";
import { cn } from "@/lib/utils";

const SPRING = { duration: 0.4, ease: [0.22, 1, 0.36, 1] as const };

/** A tiny set of well-known establishment shapes for the picker. */
const SUGGESTIONS = [
  { label: "Collège / Lycée (Index Éducation)", url: "https://0123456a.index-education.net/pronote/" },
  { label: "Espace ENT (Ile-de-France)", url: "https://ent.monlycee.fr/pronote/" },
];

export function validatePronoteUrl(raw: string): { valid: boolean; error?: string } {
  const value = raw.trim();
  if (!value) return { valid: false, error: "L'adresse de ton établissement est requise." };
  if (!/^https?:\/\//i.test(value)) {
    return { valid: false, error: "L'adresse doit commencer par https://" };
  }
  try {
    const parsed = new URL(value);
    if (!parsed.hostname.includes(".")) {
      return { valid: false, error: "Le nom de domaine semble incomplet." };
    }
    return { valid: true };
  } catch {
    return { valid: false, error: "Cette adresse n'est pas une URL valide." };
  }
}

export default function Onboarding() {
  const navigate = useNavigate();
  const { login, state } = usePronote();
  const { online } = useNetworkMonitor();
  const resolvedAccountId = useAccountId();

  const [step, setStep] = useState(0);
  const [schoolUrl, setSchoolUrl] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [urlError, setUrlError] = useState<string | undefined>();
  const [credentialError, setCredentialError] = useState<string | undefined>();
  const [hint, setHint] = useState<string | undefined>();
  const [submitting, setSubmitting] = useState(false);

  const accountId = useMemo(() => resolvedAccountId ?? "acct-local", [resolvedAccountId]);

  const connecting = submitting || state.status === "loading";

  const goNext = useCallback(() => {
    if (step === 1) {
      const result = validatePronoteUrl(schoolUrl);
      setUrlError(result.error);
      if (!result.valid) return;
    }
    setStep((s) => Math.min(2, s + 1));
  }, [step, schoolUrl]);

  const handleConnect = useCallback(async () => {
    if (connecting) return; // never fire two simultaneous requests
    setCredentialError(undefined);
    setHint(undefined);

    if (!username.trim()) {
      setCredentialError("Ton identifiant Pronote est requis.");
      return;
    }
    if (password.length < 3) {
      setCredentialError("Ton mot de passe Pronote est requis.");
      return;
    }

    setSubmitting(true);
    const result = await login({ accountId, schoolUrl, username, password });
    setSubmitting(false);

    if (result.ok) {
      navigate("/app", { replace: true });
    } else {
      setCredentialError(result.error.userMessage);
      setHint(result.error.hint);
    }
  }, [connecting, accountId, schoolUrl, username, password, login, navigate]);

  const steps = [
    {
      title: "Ta scolarité, en un coup d'œil.",
      subtitle:
        "Connecte ton compte Pronote pour retrouver tes notes et ta moyenne automatiquement.",
      cta: "Commencer",
    },
    {
      title: "Ton établissement",
      subtitle: "Renseigne l'adresse Pronote fournie par ton collège ou ton lycée.",
      cta: "Continuer",
    },
    {
      title: "Connexion",
      subtitle: "Tes identifiants sont chiffrés sur ton appareil et ne partent nulle part.",
      cta: "Se connecter",
    },
  ];

  const current = steps[step];

  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4 py-10">
      <div className="w-full max-w-lg">
        {/* progress dots */}
        <div className="mb-6 flex items-center justify-center gap-2" aria-hidden>
          {steps.map((_, index) => (
            <span
              key={index}
              className={cn(
                "clay-sm h-2.5 rounded-full transition-all duration-300",
                index === step ? "w-8" : "w-2.5",
              )}
              style={{
                backgroundColor:
                  index <= step
                    ? "color-mix(in oklab, var(--primary) 70%, var(--card))"
                    : "color-mix(in oklab, var(--foreground) 14%, var(--card))",
              }}
            />
          ))}
        </div>

        <AnimatePresence mode="wait">
          <motion.div
            key={step}
            initial={{ opacity: 0, x: 24 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -24 }}
            transition={SPRING}
          >
            <ClaySurface depth="lg" className="p-8">
              {step > 0 ? (
                <div className="mb-5 flex items-center justify-between">
                  <ClayIconButton label="Étape précédente" onClick={() => setStep((s) => s - 1)}>
                    <ArrowLeft className="size-4" aria-hidden />
                  </ClayIconButton>
                  <ClayPill tint="var(--primary)">Étape {step + 1} sur 3</ClayPill>
                </div>
              ) : null}

              <div
                className="clay mx-auto flex size-16 items-center justify-center rounded-3xl"
                style={{ backgroundColor: "color-mix(in oklab, var(--primary) 28%, var(--card))" }}
                aria-hidden
              >
                {step === 0 ? (
                  <GraduationCap className="size-7" />
                ) : step === 1 ? (
                  <Building2 className="size-7" />
                ) : (
                  <Lock className="size-7" />
                )}
              </div>

              <h1 className="mt-6 text-center text-2xl font-black tracking-tight">
                {current.title}
              </h1>
              <p className="mx-auto mt-3 max-w-sm text-center text-sm leading-relaxed text-muted-foreground">
                {current.subtitle}
              </p>

              {/* Step 1: pitch */}
              {step === 0 ? (
                <ul className="mt-7 flex flex-col gap-3">
                  {[
                    "Moyenne générale affichée instantanément",
                    "Notes, matières et évolution par période",
                    "Fonctionne hors ligne, avec cache local chiffré",
                  ].map((item) => (
                    <li
                      key={item}
                      className="clay-inset flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-medium"
                    >
                      <span
                        aria-hidden
                        className="clay-sm size-2.5 shrink-0 rounded-full"
                        style={{
                          backgroundColor: "color-mix(in oklab, var(--accent) 80%, var(--card))",
                        }}
                      />
                      {item}
                    </li>
                  ))}
                </ul>
              ) : null}

              {/* Step 2: school */}
              {step === 1 ? (
                <div className="mt-7 flex flex-col gap-5">
                  <ClayInput
                    label="Adresse Pronote"
                    value={schoolUrl}
                    onChange={(event) => {
                      setSchoolUrl(event.target.value);
                      if (urlError) setUrlError(undefined);
                    }}
                    placeholder="https://0123456a.index-education.net/pronote/"
                    inputMode="url"
                    autoComplete="url"
                    error={urlError}
                    hint="Exemple : https://xxx.index-education.net/pronote/"
                    icon={<Link2 className="size-4" aria-hidden />}
                  />

                  <div className="flex flex-col gap-2">
                    <span className="px-1 text-sm font-semibold tracking-tight text-foreground/80">
                      Établissements fréquents
                    </span>
                    <div className="flex flex-wrap gap-2">
                      {SUGGESTIONS.map((suggestion) => (
                        <button
                          key={suggestion.url}
                          type="button"
                          onClick={() => {
                            setSchoolUrl(suggestion.url);
                            setUrlError(undefined);
                          }}
                          className="clay-sm clay-pressable rounded-2xl px-3.5 py-2 text-xs font-semibold tracking-tight"
                        >
                          {suggestion.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              ) : null}

              {/* Step 3: credentials */}
              {step === 2 ? (
                <div className="mt-7 flex flex-col gap-5">
                  <ClayInput
                    label="Identifiant"
                    value={username}
                    onChange={(event) => {
                      setUsername(event.target.value);
                      setCredentialError(undefined);
                    }}
                    placeholder="Ton identifiant Pronote"
                    autoComplete="username"
                    disabled={connecting}
                    icon={<User className="size-4" aria-hidden />}
                  />

                  <ClayPasswordField
                    label="Mot de passe"
                    value={password}
                    onChange={(event) => {
                      setPassword(event.target.value);
                      setCredentialError(undefined);
                    }}
                    placeholder="••••••••"
                    disabled={connecting}
                  />

                  {credentialError ? (
                    <div
                      role="alert"
                      className="rounded-2xl bg-destructive/10 px-4 py-3 text-sm font-medium text-destructive"
                    >
                      <p>{credentialError}</p>
                      {hint ? (
                        <p className="mt-1 text-xs font-normal text-destructive/80">{hint}</p>
                      ) : null}
                    </div>
                  ) : null}

                  {!online ? (
                    <p className="rounded-2xl bg-muted px-4 py-3 text-xs font-medium text-muted-foreground">
                      Aucune connexion détectée. Reconnecte-toi à internet pour lier ton
                      compte Pronote.
                    </p>
                  ) : null}

                  <p className="flex items-start gap-2 rounded-2xl bg-card/60 px-4 py-3 text-xs leading-relaxed text-muted-foreground">
                    <ShieldCheck className="mt-0.5 size-3.5 shrink-0" aria-hidden />
                    Tes identifiants restent sur ton appareil. Nous ne contournons jamais
                    une authentification, un CAPTCHA ou une mesure de sécurité de Pronote.
                  </p>
                </div>
              ) : null}

              <div className="mt-8 flex flex-col gap-3">
                {step < 2 ? (
                  <ClayButton
                    size="lg"
                    className="w-full"
                    onClick={goNext}
                    icon={<ArrowRight className="size-5" />}
                  >
                    {current.cta}
                  </ClayButton>
                ) : (
                  <ClayButton
                    size="lg"
                    className="w-full"
                    loading={connecting}
                    disabled={!online}
                    onClick={handleConnect}
                    icon={<Lock className="size-5" />}
                  >
                    {connecting ? "Connexion à Pronote…" : current.cta}
                  </ClayButton>
                )}

                {step === 0 ? (
                  <ClayButton
                    variant="ghost"
                    className="w-full"
                    onClick={() => navigate("/")}
                    icon={<School className="size-4" />}
                  >
                    Retour à l'accueil
                  </ClayButton>
                ) : null}
              </div>
            </ClaySurface>
          </motion.div>
        </AnimatePresence>

        {connecting ? (
          <p className="mt-4 flex items-center justify-center gap-2 text-xs text-muted-foreground">
            <Loader2 className="size-3.5 animate-spin" aria-hidden />
            Connexion à Pronote…
          </p>
        ) : null}
      </div>
    </div>
  );
}
