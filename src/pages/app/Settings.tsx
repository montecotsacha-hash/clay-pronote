import { motion } from "framer-motion";
import {
  Bell,
  Database,
  Info,
  LogOut,
  Moon,
  Palette,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  Sun,
  Trash2,
  UserCog,
} from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { ClayButton } from "@/components/clay/ClayButton";
import { ClaySegmented, ClaySwitch } from "@/components/clay/ClayField";
import { ClayPill, ClaySectionTitle, ClaySurface } from "@/components/clay/ClaySurface";
import { usePronote } from "@/lib/pronote/store";
import { useTheme } from "@/lib/theme-provider";
import { clockTime, relativeTime } from "@/lib/pronote/relative-time";
import {
  notificationPermission,
  requestNotificationPermission,
} from "@/lib/pronote/notifications";
import { enrolBiometric } from "@/lib/pronote/biometric";

const SPRING = { duration: 0.4, ease: [0.22, 1, 0.36, 1] as const };

function Section({
  icon: Icon,
  title,
  children,
  delay = 0,
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  children: React.ReactNode;
  delay?: number;
}) {
  return (
    <motion.section
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ ...SPRING, delay }}
    >
      <ClaySectionTitle
        className="mb-4"
        action={
          <span
            aria-hidden
            className="clay-sm flex size-9 items-center justify-center rounded-2xl"
            style={{ backgroundColor: "color-mix(in oklab, var(--primary) 24%, var(--card))" }}
          >
            <Icon className="size-4" />
          </span>
        }
      >
        {title}
      </ClaySectionTitle>
      <ClaySurface depth="md" className="divide-y divide-border/60 px-5 py-2">
        {children}
      </ClaySurface>
    </motion.section>
  );
}

export default function Settings() {
  const {
    snapshot,
    preferences,
    updatePreferences,
    refresh,
    syncing,
    online,
    logout,
    deleteLocalData,
    session,
  } = usePronote();
  const { mode, setMode, dynamicColor, setDynamicColor } = useTheme();
  const navigate = useNavigate();
  const [permission, setPermission] = useState(notificationPermission());
  const [deleting, setDeleting] = useState(false);

  const student = snapshot?.student;

  const handleNotificationToggle = async (key: "notifyNewGrades" | "notifyAverageChange", value: boolean) => {
    if (value && permission !== "granted") {
      const next = await requestNotificationPermission();
      setPermission(next);
      if (next !== "granted") {
        toast("Notifications bloquées par le navigateur", {
          description: "Autorise les notifications dans les réglages du navigateur pour les recevoir.",
        });
        return;
      }
    }
    updatePreferences({ [key]: value });
  };

  const handleBiometricToggle = async (value: boolean) => {
    if (value) {
      const ok = await enrolBiometric();
      if (!ok) {
        toast("Verrouillage biométrique indisponible", {
          description:
            "Cet appareil n'expose pas d'authentification plateforme. Le verrouillage sera demandé à l'ouverture.",
        });
      } else {
        toast("Verrouillage biométrique activé");
      }
    }
    updatePreferences({ biometricLock: value });
  };

  const handleDelete = async () => {
    setDeleting(true);
    await deleteLocalData();
    toast("Données locales supprimées", {
      description: "Base locale, identifiants chiffrés et préférences ont été effacés.",
    });
    setDeleting(false);
    navigate("/onboarding", { replace: true });
  };

  const handleLogout = async () => {
    await logout();
    toast("Déconnexion effectuée", { description: "Tes données locales restent disponibles." });
    navigate("/");
  };

  return (
    <div className="flex flex-col gap-8">
      <h1 className="text-3xl font-black tracking-tighter">Réglages</h1>

      {/* Account */}
      <Section icon={UserCog} title="Compte Pronote">
        <div className="flex flex-col gap-1 py-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="min-w-0">
              <p className="truncate text-sm font-bold tracking-tight">
                {student ? `${student.firstName} ${student.lastName}`.trim() : "Aucun élève lié"}
              </p>
              <p className="truncate text-xs text-muted-foreground">
                {student?.schoolName || "Établissement non renseigné"}
                {student?.className ? ` · ${student.className}` : ""}
              </p>
            </div>
            <ClayPill tint={session ? "var(--chart-2)" : "var(--chart-3)"}>
              {session ? "Session active" : "Session à reconnecter"}
            </ClayPill>
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            Dernière synchronisation :{" "}
            {snapshot ? `${clockTime(snapshot.fetchedAt)} · ${relativeTime(snapshot.fetchedAt)}` : "—"}
          </p>
        </div>
        <div className="flex flex-wrap gap-3 py-4">
          <ClayButton
            variant="secondary"
            size="sm"
            onClick={() => navigate("/onboarding")}
            icon={<RefreshCw className="size-4" />}
          >
            Reconnecter
          </ClayButton>
          <ClayButton
            variant="ghost"
            size="sm"
            onClick={handleLogout}
            icon={<LogOut className="size-4" />}
          >
            Déconnexion
          </ClayButton>
        </div>
      </Section>

      {/* Sync */}
      <Section icon={RefreshCw} title="Synchronisation" delay={0.05}>
        <ClaySwitch
          label="Synchronisation automatique"
          description="Rafraîchit tes données périodiquement, uniquement avec une connexion et jamais en double."
          checked={preferences.autoSync}
          onCheckedChange={(value) => updatePreferences({ autoSync: value })}
        />
        <div className="flex flex-wrap items-center justify-between gap-3 py-4">
          <p className="text-xs text-muted-foreground">
            {online ? "Réseau disponible" : "Hors connexion — synchronisation suspendue"}
          </p>
          <ClayButton
            variant="secondary"
            size="sm"
            loading={syncing}
            disabled={!online || !snapshot}
            onClick={() => void refresh({ force: true })}
            icon={<RefreshCw className="size-4" />}
          >
            Synchroniser maintenant
          </ClayButton>
        </div>
      </Section>

      {/* Notifications */}
      <Section icon={Bell} title="Notifications" delay={0.1}>
        <ClaySwitch
          label="Nouvelles notes"
          description="Une notification locale quand une note apparaît après synchronisation."
          checked={preferences.notifyNewGrades}
          onCheckedChange={(value) => void handleNotificationToggle("notifyNewGrades", value)}
        />
        <ClaySwitch
          label="Changement de moyenne"
          description="Prévenu quand ta moyenne générale évolue."
          checked={preferences.notifyAverageChange}
          onCheckedChange={(value) => void handleNotificationToggle("notifyAverageChange", value)}
        />
        <p className="py-3 text-xs text-muted-foreground">
          Les notifications sont générées sur ton appareil après synchronisation. Aucune
          donnée scolaire n'est envoyée à un service tiers pour les créer.
          {permission !== "granted" ? " (Autorisation navigateur non accordée.)" : ""}
        </p>
      </Section>

      {/* Appearance */}
      <Section icon={Palette} title="Apparence" delay={0.15}>
        <div className="flex flex-col gap-3 py-4">
          <p className="text-sm font-semibold tracking-tight">Thème</p>
          <ClaySegmented
            label="Thème de l'application"
            options={[
              { value: "system", label: "Système" },
              { value: "light", label: "Clair" },
              { value: "dark", label: "Sombre" },
            ]}
            value={mode}
            onChange={setMode}
          />
          <p className="flex items-center gap-2 px-1 text-xs text-muted-foreground">
            {mode === "dark" ? (
              <Moon className="size-3.5" aria-hidden />
            ) : mode === "light" ? (
              <Sun className="size-3.5" aria-hidden />
            ) : (
              <Smartphone className="size-3.5" aria-hidden />
            )}
            Le thème s'applique immédiatement, en clair comme en sombre.
          </p>
        </div>
        <ClaySwitch
          label="Couleur dynamique (Material You)"
          description="Adapte les teintes de l'argile à l'accent de ton appareil."
          checked={dynamicColor}
          onCheckedChange={setDynamicColor}
        />
      </Section>

      {/* Security */}
      <Section icon={ShieldCheck} title="Sécurité" delay={0.2}>
        <ClaySwitch
          label="Verrouillage biométrique"
          description="Empreinte, reconnaissance faciale ou code de l'appareil avant d'afficher tes données."
          checked={preferences.biometricLock}
          onCheckedChange={(value) => void handleBiometricToggle(value)}
        />
        <ClaySwitch
          label="Masquer les données dans le multitâche"
          description="Floute le contenu quand l'application passe en arrière-plan."
          checked={preferences.hideInTaskSwitcher}
          onCheckedChange={(value) => updatePreferences({ hideInTaskSwitcher: value })}
        />
      </Section>

      {/* Data */}
      <Section icon={Database} title="Données" delay={0.25}>
        <div className="flex flex-wrap items-center justify-between gap-3 py-4">
          <div>
            <p className="text-sm font-semibold tracking-tight">Supprimer mes données</p>
            <p className="mt-0.5 text-xs text-muted-foreground">
              Efface le cache local, les identifiants chiffrés, les préférences et
              l'historique.
            </p>
          </div>
          <ClayButton
            variant="danger"
            size="sm"
            loading={deleting}
            onClick={handleDelete}
            icon={<Trash2 className="size-4" />}
          >
            Supprimer mes données
          </ClayButton>
        </div>
      </Section>

      {/* About */}
      <Section icon={Info} title="À propos" delay={0.3}>
        <div className="flex flex-col gap-2 py-4 text-sm">
          <div className="flex items-center justify-between">
            <span className="font-semibold tracking-tight">Formule</span>
            <span className="text-muted-foreground">Essentiel · 2,99 € par mois</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="font-semibold tracking-tight">Version</span>
            <span className="text-muted-foreground">1.0.0</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="font-semibold tracking-tight">Connecteur</span>
            <span className="text-muted-foreground">FakePronoteDataSource (démo)</span>
          </div>
          <ClayButton
            variant="secondary"
            size="sm"
            className="mt-2 self-start"
            onClick={() => navigate("/#tarifs")}
          >
            Voir les formules
          </ClayButton>
          <p className="mt-3 text-xs leading-relaxed text-muted-foreground">
            Non affilié à Index Éducation. Cette application ne contourne aucune
            authentification, aucun CAPTCHA ni aucune mesure de sécurité de Pronote, et
            n'envoie jamais tes identifiants vers un serveur qui nous appartient.
          </p>
        </div>
      </Section>
    </div>
  );
}
