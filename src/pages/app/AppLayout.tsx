import { GraduationCap, Link2, Loader2 } from "lucide-react";
import { useEffect } from "react";
import { Outlet, useNavigate } from "react-router";
import { AppShell } from "@/components/clay/AppShell";
import { BiometricGate } from "@/components/clay/BiometricGate";
import { ClayButton } from "@/components/clay/ClayButton";
import { ClaySurface } from "@/components/clay/ClaySurface";
import { usePronote } from "@/lib/pronote/store";

/**
 * AppLayout — the authenticated shell.
 *
 * The PRONOTE store itself is provided one level up (see `AuthenticatedRoot`),
 * so the onboarding step shares the same store instance and the freshly linked
 * account is available immediately after a successful login.
 */
export default function AppLayout() {
  const { state, snapshot } = usePronote();
  const navigate = useNavigate();

  const needsOnboarding = state.status === "unauthenticated" && !snapshot;

  useEffect(() => {
    if (needsOnboarding) navigate("/onboarding", { replace: true });
  }, [needsOnboarding, navigate]);

  if (state.status === "loading") {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-3">
        <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden />
        <p className="text-sm text-muted-foreground">Restauration de ta session…</p>
      </div>
    );
  }

  if (needsOnboarding) {
    return (
      <div className="flex min-h-screen items-center justify-center px-4">
        <ClaySurface depth="lg" className="max-w-md p-8 text-center">
          <span
            className="clay mx-auto flex size-16 items-center justify-center rounded-3xl"
            style={{ backgroundColor: "color-mix(in oklab, var(--primary) 28%, var(--card))" }}
          >
            <GraduationCap className="size-7" aria-hidden />
          </span>
          <h1 className="mt-5 text-xl font-black tracking-tight">Aucun compte PRONOTE lié</h1>
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
            Connecte ton compte pour afficher ta moyenne, tes notes et tes matières.
          </p>
          <ClayButton
            className="mt-6 w-full"
            onClick={() => navigate("/onboarding")}
            icon={<Link2 className="size-4" />}
          >
            Connecter mon compte
          </ClayButton>
        </ClaySurface>
      </div>
    );
  }

  return (
    <BiometricGate>
      <AppShell>
        <Outlet />
      </AppShell>
    </BiometricGate>
  );
}
