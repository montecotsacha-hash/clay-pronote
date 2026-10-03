import { motion } from "framer-motion";
import { ArrowRight, BookOpen, ListChecks, Loader2, WifiOff } from "lucide-react";
import { useMemo } from "react";
import { Link, useNavigate } from "react-router";
import { AverageCard } from "@/components/clay/AverageCard";
import { ClayButton } from "@/components/clay/ClayButton";
import { ClayPill, ClaySectionTitle, ClaySurface } from "@/components/clay/ClaySurface";
import { usePronote } from "@/lib/pronote/store";
import { formatGrade } from "@/lib/pronote/average";
import { clockTime, dayBucket, relativeTime, shortDate } from "@/lib/pronote/relative-time";
import { cn } from "@/lib/utils";

const SPRING = { duration: 0.45, ease: [0.22, 1, 0.36, 1] as const };

/**
 * Home — cache-first: Room-equivalent data is rendered immediately, then the
 * background sync updates the screen without any blocking spinner.
 */
export default function Home() {
  const { state, snapshot, refresh, syncing, online, session, lastSyncError } = usePronote();
  const navigate = useNavigate();

  const subjectRows = useMemo(() => {
    if (!snapshot) return [];
    const bySubject = new Map(snapshot.subjectAverages.map((a) => [a.subjectId, a]));
    return snapshot.subjects.map((subject) => {
      const average = bySubject.get(subject.id);
      return {
        subject,
        display: average ? average.value.toFixed(1).replace(".", ",") : "—",
        source: average?.source ?? "CALCULATED",
      };
    });
  }, [snapshot]);

  const recentGrades = useMemo(() => {
    if (!snapshot) return [];
    const subjectMap = new Map(snapshot.subjects.map((s) => [s.id, s]));
    return [...snapshot.grades]
      .sort((a, b) => b.date.localeCompare(a.date))
      .slice(0, 4)
      .map((grade) => ({
        grade,
        subject: subjectMap.get(grade.subjectId),
      }));
  }, [snapshot]);

  const average = useMemo(() => {
    const value = snapshot?.generalAverage;
    if (!value) return null;
    const delta =
      snapshot && snapshot.history.length > 1
        ? Math.round((value.value - snapshot.history[snapshot.history.length - 2].value) * 100) / 100
        : null;
    return {
      value: value.value,
      outOf: value.outOf,
      source: value.source,
      display: value.value.toFixed(1).replace(".", ","),
      delta,
      deltaDisplay: delta === null ? null : `${delta > 0 ? "+" : ""}${delta.toFixed(1)}`,
      updatedAt: value.updatedAt,
    };
  }, [snapshot]);

  // --- explicit states -----------------------------------------------------
  if (state.status === "loading" || state.status === "unauthenticated") {
    return (
      <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 text-center">
        <Loader2 className="size-7 animate-spin text-muted-foreground" aria-hidden />
        <p className="text-sm text-muted-foreground">Ouverture de ton tableau de bord…</p>
      </div>
    );
  }

  if (state.status === "error" && !snapshot) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center px-2">
        <ClaySurface depth="lg" className="max-w-md p-8 text-center">
          <h1 className="text-xl font-black tracking-tight">
            Impossible de se connecter à Pronote.
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">{state.error}</p>
          {state.hint ? (
            <p className="mt-1 text-sm font-medium text-muted-foreground">{state.hint}</p>
          ) : null}
          <ClayButton className="mt-6 w-full" onClick={() => navigate("/onboarding")}>
            Reconnecter mon compte
          </ClayButton>
        </ClaySurface>
      </div>
    );
  }

  if (!snapshot) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center px-2">
        <ClaySurface depth="lg" className="max-w-md p-8 text-center">
          <h1 className="text-xl font-black tracking-tight">Aucun compte Pronote lié</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Connecte ton établissement pour afficher ta moyenne et tes notes.
          </p>
          <ClayButton className="mt-6 w-full" onClick={() => navigate("/onboarding")}>
            Connecter mon compte
          </ClayButton>
        </ClaySurface>
      </div>
    );
  }

  const refreshing = state.status === "success" ? state.refreshing : syncing;

  return (
    <div className="flex flex-col gap-8">
      <div className="px-1">
        <h1 className="text-3xl font-black tracking-tighter">Tableau de bord</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Ta moyenne et tes dernières notes, mises à jour automatiquement.
        </p>
      </div>

      <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={SPRING}>
        <AverageCard
          average={average}
          firstName={snapshot.student.firstName}
          refreshing={refreshing}
          lastSyncAt={snapshot.fetchedAt}
          onRefresh={() => void refresh({ force: true })}
        />
      </motion.div>

      {lastSyncError ? (
        <ClaySurface depth="sm" tint="var(--chart-3)" className="flex items-center gap-3 px-4 py-3">
          <WifiOff className="size-4 shrink-0" aria-hidden />
          <p className="text-sm font-medium">
            Synchronisation impossible. Les données affichées sont celles du cache local.
          </p>
        </ClaySurface>
      ) : null}

      {/* Subjects */}
      <section>
        <ClaySectionTitle
          className="mb-4"
          action={
            <Link
              to="/app/subjects"
              className="clay-pressable flex items-center gap-1 rounded-2xl px-2 py-1 text-sm font-semibold text-muted-foreground hover:text-foreground"
            >
              Tout voir <ArrowRight className="size-4" aria-hidden />
            </Link>
          }
        >
          Mes matières
        </ClaySectionTitle>

        <div className="grid gap-3 sm:grid-cols-2">
          {subjectRows.slice(0, 6).map((row, index) => (
            <motion.div
              key={row.subject.id}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ ...SPRING, delay: 0.04 * index }}
            >
              <Link to={`/app/subjects/${row.subject.id}`} className="block">
                <ClaySurface depth="sm" pressable className="flex items-center gap-3 px-4 py-3.5">
                  <span
                    aria-hidden
                    className="size-3.5 shrink-0 rounded-full"
                    style={{ backgroundColor: row.subject.color }}
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-bold tracking-tight">
                      {row.subject.name}
                    </span>
                    <span className="block text-xs text-muted-foreground">
                      Coef. {row.subject.coefficient}
                    </span>
                  </span>
                  <span className="text-base font-black tabular-nums">{row.display}</span>
                </ClaySurface>
              </Link>
            </motion.div>
          ))}
        </div>
      </section>

      {/* Latest grades */}
      <section>
        <ClaySectionTitle
          className="mb-4"
          action={
            <Link
              to="/app/grades"
              className="clay-pressable flex items-center gap-1 rounded-2xl px-2 py-1 text-sm font-semibold text-muted-foreground hover:text-foreground"
            >
              Toutes les notes <ArrowRight className="size-4" aria-hidden />
            </Link>
          }
        >
          Dernières notes
        </ClaySectionTitle>

        <div className="flex flex-col gap-3">
          {recentGrades.length === 0 ? (
            <ClaySurface depth="sm" className="px-5 py-6 text-center">
              <BookOpen className="mx-auto size-5 text-muted-foreground" aria-hidden />
              <p className="mt-2 text-sm text-muted-foreground">
                Aucune note pour cette période. Ta moyenne s'affichera dès la première
                évaluation.
              </p>
            </ClaySurface>
          ) : (
            recentGrades.map((row, index) => (
              <motion.div
                key={row.grade.id}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ ...SPRING, delay: 0.04 * index }}
              >
                <ClaySurface depth="sm" className="flex items-center gap-4 px-4 py-3.5">
                  <span
                    aria-hidden
                    className="flex size-11 shrink-0 items-center justify-center rounded-2xl text-xs font-black tabular-nums"
                    style={{
                      backgroundColor: `color-mix(in oklab, ${row.subject?.color ?? "var(--primary)"} 30%, var(--card))`,
                    }}
                  >
                    {row.subject?.shortName ?? "—"}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-bold tracking-tight">
                      {row.subject?.name ?? "Matière"}
                    </span>
                    <span className="block truncate text-xs text-muted-foreground">
                      {row.grade.title} · {shortDate(row.grade.date)}
                    </span>
                  </span>
                  <span className="text-base font-black tabular-nums">
                    {formatGrade(row.grade.value, row.grade.maxValue)}
                  </span>
                </ClaySurface>
              </motion.div>
            ))
          )}
        </div>
      </section>

      {/* Sync footer */}
      <ClaySurface depth="sm" className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Dernière synchronisation
          </p>
          <p className="mt-0.5 text-sm font-bold tracking-tight">
            {clockTime(snapshot.fetchedAt)} · {relativeTime(snapshot.fetchedAt)}
          </p>
          <p className="mt-0.5 text-xs text-muted-foreground">
            {dayBucket(new Date(snapshot.fetchedAt).toISOString().slice(0, 10))} ·{" "}
            {online ? "En ligne" : "Hors connexion"}
          </p>
        </div>
        <ClayButton
          variant="secondary"
          loading={refreshing}
          disabled={!online}
          onClick={() => void refresh({ force: true })}
          icon={<ListChecks className="size-4" />}
        >
          Actualiser
        </ClayButton>
      </ClaySurface>

      <p className="px-1 text-center text-xs text-muted-foreground">
        Session Pronote :{" "}
        <span className={cn("font-semibold", session ? "text-foreground" : "text-muted-foreground")}>
          {session ? "active" : "à reconnecter"}
        </span>{" "}
        · <ClayPill tint="var(--chart-2)">Aucune donnée scolaire envoyée à un serveur</ClayPill>
      </p>
    </div>
  );
}
