import { motion } from "framer-motion";
import { useMemo, useState } from "react";
import { ClaySegmented } from "@/components/clay/ClayField";
import { ClayPill, ClaySectionTitle, ClaySurface } from "@/components/clay/ClaySurface";
import { usePronote } from "@/lib/pronote/store";
import { formatGrade } from "@/lib/pronote/average";
import { dayBucket, isWithinDays, longDate } from "@/lib/pronote/relative-time";
import type { Grade, Subject } from "@/lib/pronote/types";

type Filter = "all" | "today" | "week" | "period" | "subject";

const FILTERS: { value: Filter; label: string }[] = [
  { value: "all", label: "Toutes" },
  { value: "today", label: "Aujourd'hui" },
  { value: "week", label: "Cette semaine" },
  { value: "period", label: "Cette période" },
  { value: "subject", label: "Matière" },
];

export default function Grades() {
  const { snapshot } = usePronote();
  const [filter, setFilter] = useState<Filter>("all");
  const [subjectId, setSubjectId] = useState<string | null>(null);

  const subjects = snapshot?.subjects ?? [];

  const filtered = useMemo<{ grade: Grade; subject: Subject | undefined }[]>(() => {
    if (!snapshot) return [];
    const subjectMap = new Map(subjects.map((s) => [s.id, s]));

    let list = [...snapshot.grades];
    if (filter === "today") list = list.filter((g) => isWithinDays(g.date, 1));
    if (filter === "week") list = list.filter((g) => isWithinDays(g.date, 7));
    if (filter === "period") list = list.filter((g) => isWithinDays(g.date, 90));
    if ((filter === "subject" || subjectId) && subjectId) {
      list = list.filter((g) => g.subjectId === subjectId);
    }

    return list
      .sort((a, b) => b.date.localeCompare(a.date))
      .map((grade) => ({ grade, subject: subjectMap.get(grade.subjectId) }));
  }, [snapshot, subjects, filter, subjectId]);

  /** Group into the timeline buckets the spec asks for. */
  const grouped = useMemo(() => {
    const map = new Map<string, typeof filtered>();
    for (const row of filtered) {
      const bucket = dayBucket(row.grade.date);
      const list = map.get(bucket) ?? [];
      list.push(row);
      map.set(bucket, list);
    }
    return [...map.entries()];
  }, [filtered]);

  if (!snapshot) {
    return (
      <ClaySurface depth="lg" className="p-8 text-center">
        <p className="text-sm text-muted-foreground">
          Connecte ton établissement pour voir tes notes.
        </p>
      </ClaySurface>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <ClaySectionTitle>Mes notes</ClaySectionTitle>

      <ClaySegmented
        label="Filtrer les notes par période"
        options={FILTERS}
        value={filter}
        onChange={(next) => {
          setFilter(next);
          if (next !== "subject") setSubjectId(null);
        }}
      />

      {filter === "subject" ? (
        <div className="flex flex-wrap gap-2">
          {subjects.map((subject) => {
            const active = subject.id === subjectId;
            return (
              <button
                key={subject.id}
                type="button"
                aria-pressed={active}
                onClick={() => setSubjectId(active ? null : subject.id)}
                className="clay-sm clay-pressable rounded-2xl px-3.5 py-2 text-xs font-semibold tracking-tight"
                style={
                  active
                    ? {
                        backgroundColor: `color-mix(in oklab, ${subject.color} 34%, var(--card))`,
                      }
                    : undefined
                }
              >
                {subject.name}
              </button>
            );
          })}
        </div>
      ) : null}

      {grouped.length === 0 ? (
        <ClaySurface depth="sm" className="px-5 py-8 text-center">
          <p className="text-sm text-muted-foreground">
            Aucune note ne correspond à ce filtre.
          </p>
        </ClaySurface>
      ) : (
        grouped.map(([bucket, rows], groupIndex) => (
          <section key={bucket}>
            <div className="mb-3 flex items-center gap-3 px-1">
              <h3 className="text-sm font-bold uppercase tracking-wider text-muted-foreground">
                {bucket}
              </h3>
              <span className="h-px flex-1 bg-border" aria-hidden />
              <ClayPill tint="var(--primary)">{rows.length}</ClayPill>
            </div>

            <div className="flex flex-col gap-3">
              {rows.map((row, index) => (
                <motion.div
                  key={row.grade.id}
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{
                    duration: 0.35,
                    delay: Math.min(groupIndex * 0.05 + index * 0.03, 0.35),
                    ease: [0.22, 1, 0.36, 1],
                  }}
                >
                  <ClaySurface depth="sm" className="flex items-center gap-4 px-5 py-4">
                    <span
                      aria-hidden
                      className="flex size-12 shrink-0 items-center justify-center rounded-2xl text-xs font-black"
                      style={{
                        backgroundColor: `color-mix(in oklab, ${row.subject?.color ?? "var(--primary)"} 32%, var(--card))`,
                      }}
                    >
                      {row.subject?.shortName ?? "—"}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-bold tracking-tight">
                        {row.grade.title}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">
                        {row.subject?.name ?? "Matière"} · {longDate(row.grade.date)} · Coef.{" "}
                        {row.grade.coefficient}
                      </p>
                    </div>
                    <span className="text-lg font-black tabular-nums">
                      {formatGrade(row.grade.value, row.grade.maxValue)}
                    </span>
                  </ClaySurface>
                </motion.div>
              ))}
            </div>
          </section>
        ))
      )}
    </div>
  );
}
