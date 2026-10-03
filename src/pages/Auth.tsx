import { ArrowRight, GraduationCap, Loader2, Mail, ShieldCheck, UserX } from "lucide-react";
import { motion } from "framer-motion";
import { Suspense, useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { ClayButton } from "@/components/clay/ClayButton";
import { ClayInput } from "@/components/clay/ClayField";
import { ClayPill, ClaySurface } from "@/components/clay/ClaySurface";
import { useAuth } from "@/hooks/use-auth";

interface AuthProps {
  redirectAfterAuth?: string;
}

function resolveRedirectAfterAuth(returnTo: string | null, fallback = "/app") {
  if (returnTo?.startsWith("/") && !returnTo.startsWith("//")) return returnTo;
  return fallback;
}

const SPRING = { duration: 0.45, ease: [0.22, 1, 0.36, 1] as const };

function Auth({ redirectAfterAuth }: AuthProps = {}) {
  const { isLoading: authLoading, isAuthenticated, signIn } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const redirect = resolveRedirectAfterAuth(searchParams.get("returnTo"), redirectAfterAuth);

  const [step, setStep] = useState<"signIn" | { email: string }>("signIn");
  const [otp, setOtp] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!authLoading && isAuthenticated) navigate(redirect);
  }, [authLoading, isAuthenticated, navigate, redirect]);

  const handleEmailSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsLoading(true);
    setError(null);
    try {
      const formData = new FormData(event.currentTarget);
      await signIn("email-otp", formData);
      setStep({ email: formData.get("email") as string });
    } catch (err) {
      console.error("Email sign-in error:", err);
      setError("Le code n'a pas pu être envoyé. Réessaie dans un instant.");
    } finally {
      setIsLoading(false);
    }
  };

  const handleOtpSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsLoading(true);
    setError(null);
    try {
      const formData = new FormData(event.currentTarget);
      await signIn("email-otp", formData);
      navigate(redirect);
    } catch (err) {
      console.error("OTP verification error:", err);
      setError("Le code saisi est incorrect.");
      setOtp("");
      setIsLoading(false);
    }
  };

  const handleGuestLogin = async () => {
    setIsLoading(true);
    setError(null);
    try {
      await signIn("anonymous");
      navigate(redirect);
    } catch (err) {
      console.error("Guest login error:", err);
      setError("La connexion invité a échoué. Réessaie.");
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <motion.div
        initial={{ opacity: 0, y: 20, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={SPRING}
        className="w-full max-w-md"
      >
        <ClaySurface depth="lg" className="p-8">
          <button
            type="button"
            onClick={() => navigate("/")}
            className="clay-pressable mx-auto flex size-16 items-center justify-center rounded-3xl"
            style={{ backgroundColor: "color-mix(in oklab, var(--primary) 30%, var(--card))" }}
            aria-label="Retour à l'accueil"
          >
            <GraduationCap className="size-7" aria-hidden />
          </button>

          {step === "signIn" ? (
            <>
              <div className="mt-6 text-center">
                <h1 className="text-2xl font-black tracking-tight">Bienvenue</h1>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                  Crée ton compte pour suivre ta moyenne, tes notes et tes matières.
                  L'essai est gratuit, sans engagement.
                </p>
              </div>

              <form onSubmit={handleEmailSubmit} className="mt-7 flex flex-col gap-5">
                <ClayInput
                  label="Adresse e-mail"
                  name="email"
                  type="email"
                  placeholder="prenom@exemple.com"
                  autoComplete="email"
                  required
                  disabled={isLoading}
                  icon={<Mail className="size-4" aria-hidden />}
                />

                {error ? (
                  <p className="rounded-2xl bg-destructive/10 px-4 py-2.5 text-sm font-medium text-destructive">
                    {error}
                  </p>
                ) : null}

                <ClayButton
                  type="submit"
                  size="lg"
                  loading={isLoading}
                  className="w-full"
                  icon={<ArrowRight className="size-5" />}
                >
                  Recevoir mon code
                </ClayButton>
              </form>

              <div className="my-6 flex items-center gap-3" aria-hidden>
                <span className="h-px flex-1 bg-border" />
                <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  ou
                </span>
                <span className="h-px flex-1 bg-border" />
              </div>

              <ClayButton
                type="button"
                variant="secondary"
                size="lg"
                className="w-full"
                onClick={handleGuestLogin}
                disabled={isLoading}
                icon={<UserX className="size-5" />}
              >
                Continuer en mode découverte
              </ClayButton>
            </>
          ) : (
            <>
              <div className="mt-6 text-center">
                <h1 className="text-2xl font-black tracking-tight">Vérifie ta boîte mail</h1>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                  Nous avons envoyé un code à 6 chiffres à{" "}
                  <span className="font-semibold text-foreground">{step.email}</span>.
                </p>
              </div>

              <form onSubmit={handleOtpSubmit} className="mt-7 flex flex-col gap-5">
                <input type="hidden" name="email" value={step.email} />
                <input type="hidden" name="code" value={otp} />

                <div className="flex flex-col gap-2">
                  <span className="px-1 text-sm font-semibold tracking-tight text-foreground/80">
                    Code de vérification
                  </span>
                  <div className="clay-inset flex items-center justify-center gap-2 rounded-2xl px-4 py-4">
                    <input
                      aria-label="Code de vérification à 6 chiffres"
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      maxLength={6}
                      value={otp}
                      onChange={(event) => setOtp(event.target.value.replace(/\D/g, ""))}
                      disabled={isLoading}
                      className="w-full max-w-[15rem] bg-transparent text-center text-2xl font-black tracking-[0.5em] tabular-nums outline-none placeholder:tracking-[0.5em] placeholder:text-muted-foreground/50"
                      placeholder="000000"
                    />
                  </div>
                </div>

                {error ? (
                  <p className="rounded-2xl bg-destructive/10 px-4 py-2.5 text-sm font-medium text-destructive">
                    {error}
                  </p>
                ) : null}

                <ClayButton
                  type="submit"
                  size="lg"
                  className="w-full"
                  loading={isLoading}
                  disabled={otp.length !== 6}
                  icon={<ArrowRight className="size-5" />}
                >
                  Vérifier le code
                </ClayButton>

                <ClayButton
                  type="button"
                  variant="ghost"
                  className="w-full"
                  onClick={() => {
                    setStep("signIn");
                    setOtp("");
                    setError(null);
                  }}
                  disabled={isLoading}
                >
                  Utiliser une autre adresse
                </ClayButton>
              </form>
            </>
          )}

          <div className="mt-7 flex flex-col items-center gap-2 border-t border-border/60 pt-5">
            <ClayPill tint="var(--accent)">
              <ShieldCheck className="size-3.5" aria-hidden />
              Tes données restent sur ton appareil
            </ClayPill>
            <p className="text-center text-xs text-muted-foreground">
              Ce compte sert à identifier ton espace et à gérer ton abonnement. Aucune
              donnée scolaire n'est envoyée sur un serveur.
            </p>
          </div>
        </ClaySurface>

        {isLoading ? (
          <p className="mt-4 flex items-center justify-center gap-2 text-xs text-muted-foreground">
            <Loader2 className="size-3.5 animate-spin" aria-hidden />
            Traitement en cours…
          </p>
        ) : null}
      </motion.div>
    </div>
  );
}

export default function AuthPage(props: AuthProps) {
  return (
    <Suspense>
      <Auth {...props} />
    </Suspense>
  );
}
