import { motion } from "framer-motion";
import { Fingerprint, Lock, ScanFace, ShieldCheck } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ClayButton } from "./ClayButton";
import { ClaySurface } from "./ClaySurface";
import { usePronote } from "@/lib/pronote/store";
import { isBiometricAvailable, verifyBiometric } from "@/lib/pronote/biometric";

/**
 * BiometricGate — the "Protéger l'application" lock.
 *
 * Uses WebAuthn's platform authenticator (fingerprint / face / device PIN) when
 * available, and falls back to an explicit confirmation step otherwise. Until it
 * is unlocked, no school data is rendered at all.
 */
export function BiometricGate({ children }: { children: React.ReactNode }) {
  const { preferences } = usePronote();
  const [locked, setLocked] = useState(preferences.biometricLock);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [available, setAvailable] = useState<boolean | null>(null);

  useEffect(() => {
    let cancelled = false;
    void isBiometricAvailable().then((ok) => {
      if (!cancelled) setAvailable(ok);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const unlock = useCallback(async () => {
    setBusy(true);
    setError(null);
    const ok = await verifyBiometric();
    if (ok) {
      setLocked(false);
    } else {
      setError("Vérification annulée. Réessaie pour accéder à tes données.");
    }
    setBusy(false);
  }, []);

  if (!preferences.biometricLock || !locked) return <>{children}</>;

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <motion.div
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
        className="w-full max-w-md"
      >
        <ClaySurface depth="lg" tint="var(--primary)" className="p-8 text-center">
          <div
            className="clay mx-auto flex size-20 items-center justify-center rounded-3xl"
            style={{ backgroundColor: "color-mix(in oklab, var(--primary) 26%, var(--card))" }}
          >
            {available === false ? (
              <Lock className="size-8" aria-hidden />
            ) : (
              <Fingerprint className="size-8" aria-hidden />
            )}
          </div>

          <h1 className="mt-6 text-2xl font-black tracking-tight">Application protégée</h1>
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
            {available === false
              ? "Aucun capteur biométrique détecté. Confirme pour afficher tes données scolaires."
              : "Utilise ton empreinte, ta reconnaissance faciale ou le code de ton appareil."}
          </p>

          {error ? (
            <p className="mt-4 rounded-2xl bg-destructive/10 px-4 py-2.5 text-sm font-medium text-destructive">
              {error}
            </p>
          ) : null}

          <ClayButton
            size="lg"
            className="mt-6 w-full"
            loading={busy}
            onClick={unlock}
            icon={available === false ? <ShieldCheck className="size-5" /> : <ScanFace className="size-5" />}
          >
            Déverrouiller
          </ClayButton>

          <p className="mt-4 flex items-center justify-center gap-1.5 text-xs text-muted-foreground">
            <ShieldCheck className="size-3.5" aria-hidden />
            Tes données ne quittent jamais ton appareil.
          </p>
        </ClaySurface>
      </motion.div>
    </div>
  );
}
