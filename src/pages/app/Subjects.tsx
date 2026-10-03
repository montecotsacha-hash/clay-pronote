import { motion } from "framer-motion";
import { ArrowDownRight, ArrowRight, ArrowUpRight, Minus } from "lucide-react";
import { useMemo } from "react";
import { Link } from "react-router";
import { ClayPill, ClaySectionTitle, ClaySurface } from "@/components/clay/ClaySurface";
import { usePronote } from "@/lib/pronote/store";
import { subjectAverage, subjectTrend } from "@/lib/pronote/average";
import { cn } from "@/lib/utils";

const SPRING = { duration: 0.4, ease: [0.22, 1, 0.36, 1] as const };

export default function Subjects() {
  const { snapshot } = usePronote();

  const rows = useMemo(() => {
    if (!snapshot) return [];
    const bySubject = new Map(snapshot.subjectAverages.map((a) => [a.subjectId, a]));
    return snapshot.subjects.map((subject) => {
      const official = bySubject.get(subject.id);
      const computed = subjectAverage(subject, snapshot.grades);
      const value = official?.value ?? computed;
      const delta = subjectTrend(subject, snapshot.grades);
      return {
        subject,
        value,
        display: value === null ? "—" : value.toFixed(1).replace(".", ","),
        delta,
        gradeCount: snapshot.grades.filter((g) => g.subjectId === subject.id).length,
        source: official?.source ?? "CALCULATED",
      };
    });
  }, [snapshot]);

  if (!snapshot) {
    return (
      <ClaySurface depth="lg" className="p-8 text-center">
        <p className="text-sm text-muted-foreground">
          Connecte ton établissement pour voir tes matières.
        </p>
      </ClaySurface>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <ClaySectionTitle>Mes matières</ClaySectionTitle>

      <div className="grid gap-3">
        {rows.map((row, index) => {
          const isUp = row.delta !== null && row.delta > 0.001;
          const isDown = row.delta !== null && row.delta < -0.001;

          return (
            <motion.div
              key={row.subject.id}
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ ...SPRING, delay: index * 0.04 }}
            >
              <Link to={`/app/subjects/${row.subject.id}`} className="block">
                <ClaySurface depth="md" pressable tint={row.subject.color} className="px-5 py-4">
                  <div className="flex items-center gap-4">
                    <span
                      aria-hidden
                      className="flex size-12 shrink-0 items-center justify-center rounded-2xl text-sm font-black"
                      style={{
                        backgroundColor: `color-mix(in oklab, ${row.subject.color} 32%, var(--card))`,
                      }}
                    >
                      {row.subject.shortName}
                    </span>

                    <div className="min-w-0 flex-1">
                      <p className="truncate text-base font-bold tracking-tight">
                        {row.subject.name}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">
                        {row.subject.teacher || "Professeur non renseigné"} · Coef.{" "}
                        {row.subject.coefficient} · {row.gradeCount} note
                        {row.gradeCount > 1 ? "s" : ""}
                      </p>
                    </div>

                    <div className="flex flex-col items-end gap-1">
                      <span className="text-xl font-black tabular-nums">{row.display}</span>
                      {row.delta !== null ? (
                        <span
                          className={cn(
                            "flex items-center gap-1 text-xs font-bold tabular-nums",
                            isUp
                              ? "text-foreground"
                              : isDown
                                ? "text-destructive"
                                : "text-muted-foreground",
                          )}
                        >
                          {isUp ? (
                            <ArrowUpRight className="size-3.5" aria-hidden />
                          ) : isDown ? (
                            <ArrowDownRight className="size-3.5" aria-hidden />
                          ) : (
                            <Minus className="size-3.5" aria-hidden />
                          )}
                          {row.delta > 0 ? "+" : ""}
                          {row.delta.toFixed(1).replace(".", ",")}
                        </span>
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </div>

                    <ArrowRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                  </div>

                  {row.source === "OFFICIAL" ? (
                    <ClayPill tint="var(--chart-2)" className="mt-3 ml-16">
                      Moyenne officielle Pronote
                    </ClayPill>
                  ) : (
                    <ClayPill tint="var(--chart-3)" className="mt-3 ml-16">
                      Calculée localement
                    </ClayPill>
                  )}
                </ClaySurface>
              </Link>
            </motion.div>
          );
        })}
      </div>
    </div>
  );
}
