import { motion } from "framer-motion";
import { ArrowLeft, TrendingUp } from "lucide-react";
import { useMemo } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { ClayIconButton } from "@/components/clay/ClayButton";
import { ClayPill, ClaySectionTitle, ClaySurface, ClayWell } from "@/components/clay/ClaySurface";
import { usePronote } from "@/lib/pronote/store";
import { formatGrade, normalizeGrade, round2, subjectAverage } from "@/lib/pronote/average";
import { longDate } from "@/lib/pronote/relative-time";

const SPRING = { duration: 0.4, ease: [0.22, 1, 0.36, 1] as const };

/** A small hand-drawn clay line chart — no chart library needed. */
function ProgressionChart({ points, color }: { points: number[]; color: string }) {
  if (points.length < 2) {
    return (
      <ClayWell className="flex h-32 items-center justify-center px-4 text-center">
        <p className="text-xs text-muted-foreground">
          Pas assez de notes pour tracer une progression.
        </p>
      </ClayWell>
    );
  }

  const width = 320;
  const height = 120;
  const padding = 10;
  const min = Math.min(...points, 0);
  const max = Math.max(...points, 20);
  const span = max - min || 1;

  const coords = points.map((value, index) => {
    const x = padding + (index / (points.length - 1)) * (width - padding * 2);
    const y = height - padding - ((value - min) / span) * (height - padding * 2);
    return `${x},${y}`;
  });

  const line = coords.join(" ");
  const area = `${padding},${height - padding} ${line} ${width - padding},${height - padding}`;

  return (
    <ClayWell className="p-4">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="h-32 w-full"
        role="img"
        aria-label={`Progression de la moyenne : ${points.map((p) => p.toFixed(1)).join(", ")}`}
      >
        <defs>
          <linearGradient id="clayArea" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.45" />
            <stop offset="100%" stopColor={color} stopOpacity="0.05" />
          </linearGradient>
        </defs>
        <polygon points={area} fill="url(#clayArea)" />
        <polyline
          points={line}
          fill="none"
          stroke={color}
          strokeWidth="4"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {coords.map((coord, index) => {
          const [x, y] = coord.split(",");
          return (
            <circle
              key={index}
              cx={x}
              cy={y}
              r="5"
              fill="var(--card)"
              stroke={color}
              strokeWidth="3"
            />
          );
        })}
      </svg>
    </ClayWell>
  );
}

export default function SubjectDetails() {
  const { subjectId } = useParams<{ subjectId: string }>();
  const navigate = useNavigate();
  const { snapshot } = usePronote();

  const subject = snapshot?.subjects.find((s) => s.id === subjectId);

  const grades = useMemo(() => {
    if (!snapshot || !subject) return [];
    return snapshot.grades
      .filter((g) => g.subjectId === subject.id)
      .sort((a, b) => b.date.localeCompare(a.date));
  }, [snapshot, subject]);

  const average = useMemo(() => {
    if (!snapshot || !subject) return null;
    const official = snapshot.subjectAverages.find((a) => a.subjectId === subject.id);
    return { value: official?.value ?? subjectAverage(subject, snapshot.grades), source: official?.source ?? "CALCULATED" };
  }, [snapshot, subject]);

  if (!snapshot || !subject) {
    return (
      <ClaySurface depth="lg" className="p-8 text-center">
        <p className="text-sm text-muted-foreground">Cette matière est introuvable.</p>
        <Link
          to="/app/subjects"
          className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-foreground"
        >
          <ArrowLeft className="size-4" aria-hidden /> Retour aux matières
        </Link>
      </ClaySurface>
    );
  }

  const progression = [...grades]
    .sort((a, b) => a.date.localeCompare(b.date))
    .filter((g) => g.countsTowardAverage)
    .map((g) => round2(normalizeGrade(g)));

  const countedGrades = grades.filter((g) => g.countsTowardAverage);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-3">
        <ClayIconButton label="Retour aux matières" onClick={() => navigate("/app/subjects")}>
          <ArrowLeft className="size-4" aria-hidden />
        </ClayIconButton>
        <div className="min-w-0">
          <h1 className="truncate text-2xl font-black tracking-tight">{subject.name}</h1>
          <p className="truncate text-sm text-muted-foreground">
            {subject.teacher || "Professeur non renseigné"} · Coef. {subject.coefficient}
          </p>
        </div>
      </div>

      <motion.div initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={SPRING}>
        <ClaySurface depth="lg" tint={subject.color} className="p-6">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                Moyenne de la matière
              </p>
              <div className="mt-2 flex items-end gap-2">
                <span className="text-5xl font-black leading-none tracking-tighter tabular-nums">
                  {average?.value !== null && average?.value !== undefined
                    ? average.value.toFixed(1).replace(".", ",")
                    : "—"}
                </span>
                <span className="pb-1 text-xl font-bold text-muted-foreground">/20</span>
              </div>
            </div>
            <div className="flex flex-col items-end gap-2">
              {average?.source === "OFFICIAL" ? (
                <ClayPill tint="var(--chart-2)">Officielle PRONOTE</ClayPill>
              ) : (
                <ClayPill tint="var(--chart-3)">Calculée localement</ClayPill>
              )}
              <p className="text-xs text-muted-foreground">
                {countedGrades.length} note{countedGrades.length > 1 ? "s" : ""} prise
                {countedGrades.length > 1 ? "s" : ""} en compte
              </p>
            </div>
          </div>
        </ClaySurface>
      </motion.div>

      <section>
        <ClaySectionTitle
          className="mb-4"
          action={
            <span className="flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
              <TrendingUp className="size-3.5" aria-hidden /> Progression
            </span>
          }
        >
          Graphique
        </ClaySectionTitle>
        <ProgressionChart points={progression} color={subject.color} />
      </section>

      <section>
        <ClaySectionTitle className="mb-4">Notes</ClaySectionTitle>
        <div className="flex flex-col gap-3">
          {grades.length === 0 ? (
            <ClaySurface depth="sm" className="px-5 py-6 text-center">
              <p className="text-sm text-muted-foreground">Aucune note pour cette matière.</p>
            </ClaySurface>
          ) : (
            grades.map((grade, index) => (
              <motion.div
                key={grade.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ ...SPRING, delay: index * 0.03 }}
              >
                <ClaySurface depth="sm" className="flex items-center gap-4 px-5 py-4">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-bold tracking-tight">{grade.title}</p>
                    <p className="mt-0.5 truncate text-xs text-muted-foreground">
                      {longDate(grade.date)} · Coef. {grade.coefficient}
                      {!grade.countsTowardAverage ? " · non significatif" : ""}
                    </p>
                    {grade.comment ? (
                      <p className="mt-1.5 rounded-xl bg-card/70 px-3 py-1.5 text-xs italic text-muted-foreground">
                        « {grade.comment} »
                      </p>
                    ) : null}
                  </div>
                  <span className="text-lg font-black tabular-nums">
                    {formatGrade(grade.value, grade.maxValue)}
                  </span>
                </ClaySurface>
              </motion.div>
            ))
          )}
        </div>
      </section>
    </div>
  );
}
