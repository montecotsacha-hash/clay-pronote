import { motion } from "framer-motion";
import { useMemo, useState } from "react";
import { ClaySegmented } from "@/components/clay/ClayField";
import { ClayPill, ClaySectionTitle, ClaySurface, ClayWell } from "@/components/clay/ClaySurface";
import { usePronote } from "@/lib/pronote/store";
import { monthLabel, shortDate } from "@/lib/pronote/relative-time";

type Range = "week" | "month" | "term" | "year";

const RANGES: { value: Range; label: string }[] = [
  { value: "week", label: "Semaine" },
  { value: "month", label: "Mois" },
  { value: "term", label: "Trimestre" },
  { value: "year", label: "Année" },
];

const DAYS: Record<Range, number> = { week: 7, month: 31, term: 100, year: 400 };

export default function History() {
  const { snapshot } = usePronote();
  const [range, setRange] = useState<Range>("term");
  // Captured once per mount so render stays pure (no Date.now during render).
  const [referenceTime] = useState(() => Date.now());

  const points = useMemo(() => {
    if (!snapshot) return [];
    const cutoff = referenceTime - DAYS[range] * 86_400_000;
    const filtered = snapshot.history.filter(
      (point) => new Date(`${point.date}T00:00:00`).getTime() >= cutoff,
    );
    // Always keep at least the last few points so the chart is never empty.
    const list = filtered.length >= 2 ? filtered : snapshot.history.slice(-6);
    return list;
  }, [snapshot, range]);

  const stats = useMemo(() => {
    if (points.length === 0) return null;
    const values = points.map((p) => p.value);
    return {
      min: Math.min(...values),
      max: Math.max(...values),
      latest: values[values.length - 1],
      first: values[0],
    };
  }, [points]);

  if (!snapshot) {
    return (
      <ClaySurface depth="lg" className="p-8 text-center">
        <p className="text-sm text-muted-foreground">
          Connecte ton compte PRONOTE pour voir l'évolution de ta moyenne.
        </p>
      </ClaySurface>
    );
  }

  const values = points.map((p) => p.value);
  const width = 360;
  const height = 180;
  const padding = 16;
  const min = 0;
  const max = 20;
  const span = max - min || 1;

  const coords = values.map((value, index) => {
    const x =
      values.length === 1
        ? width / 2
        : padding + (index / (values.length - 1)) * (width - padding * 2);
    const y = height - padding - ((value - min) / span) * (height - padding * 2);
    return { x, y };
  });

  const line = coords.map((c) => `${c.x},${c.y}`).join(" ");
  const area = coords.length
    ? `${coords[0].x},${height - padding} ${line} ${coords[coords.length - 1].x},${height - padding}`
    : "";

  return (
    <div className="flex flex-col gap-6">
      <ClaySectionTitle>Évolution de la moyenne</ClaySectionTitle>

      <ClaySegmented
        label="Choisir la période d'historique"
        options={RANGES}
        value={range}
        onChange={setRange}
      />

      {stats ? (
        <div className="grid gap-3 sm:grid-cols-3">
          {[
            { label: "Moyenne actuelle", value: stats.latest, tint: "var(--primary)" },
            { label: "Minimum", value: stats.min, tint: "var(--chart-4)" },
            { label: "Maximum", value: stats.max, tint: "var(--chart-2)" },
          ].map((stat, index) => (
            <motion.div
              key={stat.label}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4, delay: index * 0.05, ease: [0.22, 1, 0.36, 1] }}
            >
              <ClaySurface depth="sm" tint={stat.tint} className="px-5 py-4">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {stat.label}
                </p>
                <p className="mt-1 text-2xl font-black tabular-nums">
                  {stat.value.toFixed(1).replace(".", ",")}
                </p>
              </ClaySurface>
            </motion.div>
          ))}
        </div>
      ) : null}

      <ClaySurface depth="lg" className="p-5">
        {coords.length >= 2 ? (
          <ClayWell className="p-4">
            <svg
              viewBox={`0 0 ${width} ${height}`}
              className="h-48 w-full"
              role="img"
              aria-label={`Évolution de la moyenne générale : ${values
                .map((v) => v.toFixed(1))
                .join(", ")}`}
            >
              <defs>
                <linearGradient id="historyArea" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.4" />
                  <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.03" />
                </linearGradient>
              </defs>

              {[0, 5, 10, 15, 20].map((tick) => {
                const y = height - padding - ((tick - min) / span) * (height - padding * 2);
                return (
                  <g key={tick}>
                    <line
                      x1={padding}
                      x2={width - padding}
                      y1={y}
                      y2={y}
                      stroke="var(--border)"
                      strokeWidth="1"
                      strokeDasharray="3 5"
                    />
                    <text
                      x={4}
                      y={y + 4}
                      fontSize="9"
                      fill="var(--muted-foreground)"
                    >
                      {tick}
                    </text>
                  </g>
                );
              })}

              <polygon points={area} fill="url(#historyArea)" />
              <polyline
                points={line}
                fill="none"
                stroke="var(--primary)"
                strokeWidth="4"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              {coords.map((coord, index) => (
                <circle
                  key={index}
                  cx={coord.x}
                  cy={coord.y}
                  r="4.5"
                  fill="var(--card)"
                  stroke="var(--primary)"
                  strokeWidth="3"
                />
              ))}
            </svg>
          </ClayWell>
        ) : (
          <p className="py-10 text-center text-sm text-muted-foreground">
            Pas encore assez de données pour tracer une courbe. Synchronise après
            quelques notes et l'évolution apparaîtra ici.
          </p>
        )}
      </ClaySurface>

      <div className="flex flex-wrap gap-2">
        {points.map((point, index) => (
          <ClayPill key={`${point.date}-${index}`} tint="var(--primary)">
            {monthLabel(point.date)} · {shortDate(point.date)} — {point.value.toFixed(1).replace(".", ",")}
          </ClayPill>
        ))}
      </div>

      <p className="px-1 text-xs text-muted-foreground">
        Source de la moyenne affichée :{" "}
        {snapshot.generalAverage?.source === "OFFICIAL"
          ? "PRONOTE (moyenne officielle)"
          : "calcul local à partir de tes notes et coefficients"}
        .
      </p>
    </div>
  );
}
