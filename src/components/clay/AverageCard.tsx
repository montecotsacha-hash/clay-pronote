import { motion, useReducedMotion } from "framer-motion";
import { ArrowDownRight, ArrowUpRight, Minus, Sparkles } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { ClayPill, ClaySurface } from "./ClaySurface";
import { ClayIconButton } from "./ClayButton";
import { RefreshCw } from "lucide-react";
import { formatDelta } from "@/lib/pronote/average";
import type { AverageSummary } from "@/lib/pronote/use-cases";
import { clockTime, relativeTime } from "@/lib/pronote/relative-time";
import { cn } from "@/lib/utils";

/**
 * AverageCard — the hero of the home screen.
 *
 * The number animates over ~320 ms whenever it changes (spec: 250–400 ms), and
 * the direction is always shown with an arrow *and* a sign, never colour alone.
 */
export function AverageCard({
  average,
  firstName,
  refreshing,
  onRefresh,
  lastSyncAt,
  className,
}: {
  average: AverageSummary | null;
  firstName: string;
  refreshing: boolean;
  onRefresh: () => void;
  lastSyncAt: number | null;
  className?: string;
}) {
  const reduceMotion = useReducedMotion();
  const target = average?.value ?? 0;
  const [display, setDisplay] = useState(target);
  const [pulse, setPulse] = useState(0);
  const previous = useRef<number | null>(average?.value ?? null);

  // Animate the value between the old and the new number over ~340 ms.
  useEffect(() => {
    const from = previous.current;
    const to = target;
    previous.current = to;

    if (from === null || from === to || reduceMotion) {
      // Deferred by one frame so the effect never triggers a cascading render.
      const settle = requestAnimationFrame(() => setDisplay(to));
      return () => cancelAnimationFrame(settle);
    }

    const bump = requestAnimationFrame(() => setPulse((p) => p + 1));
    const start = performance.now();
    const duration = 340;
    let frame = 0;

    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      setDisplay(from + (to - from) * eased);
      if (t < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);

    return () => {
      cancelAnimationFrame(frame);
      cancelAnimationFrame(bump);
    };
  }, [target, reduceMotion]);

  const delta = average?.delta ?? null;
  const isUp = delta !== null && delta > 0.001;
  const isDown = delta !== null && delta < -0.001;

  return (
    <ClaySurface
      depth="lg"
      tint="var(--primary)"
      className={cn("overflow-hidden p-6 sm:p-8", className)}
    >
      {/* decorative clay blobs */}
      <div
        aria-hidden
        className="animate-clay-float pointer-events-none absolute -right-8 -top-10 size-32 rounded-full opacity-60 blur-[2px]"
        style={{ backgroundColor: "color-mix(in oklab, var(--accent) 55%, transparent)" }}
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -bottom-12 -left-6 size-28 rounded-full opacity-40 blur-[2px]"
        style={{ backgroundColor: "color-mix(in oklab, var(--chart-4) 50%, transparent)" }}
      />

      <div className="relative flex items-start justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-muted-foreground">
            Bonjour,
          </p>
          <p className="text-xl font-bold tracking-tight">{firstName}</p>
        </div>
        <ClayIconButton
          label="Actualiser mes données Pronote"
          loading={refreshing}
          onClick={onRefresh}
        >
          <RefreshCw className="size-4" aria-hidden />
        </ClayIconButton>
      </div>

      <div className="relative mt-6">
        <div className="flex items-center gap-2">
          <p className="text-sm font-semibold uppercase tracking-[0.14em] text-muted-foreground">
            Moyenne générale
          </p>
          {average?.source === "OFFICIAL" ? (
            <ClayPill tint="var(--chart-2)">
              <Sparkles className="size-3" aria-hidden /> Officielle
            </ClayPill>
          ) : average ? (
            <ClayPill tint="var(--chart-3)">Calculée localement</ClayPill>
          ) : null}
        </div>

        <div className="mt-3 flex flex-wrap items-end gap-x-3 gap-y-2">
          <span
            key={pulse}
            className={cn(
              "clay-pop text-6xl font-black leading-none tracking-tighter tabular-nums sm:text-7xl",
              !reduceMotion && "animate-clay-pop",
            )}
            aria-live="polite"
            aria-label={
              average ? `Moyenne générale ${average.display} sur 20` : "Moyenne indisponible"
            }
          >
            {average ? display.toFixed(1).replace(".", ",") : "—"}
          </span>
          <span className="pb-2 text-2xl font-bold text-muted-foreground">/20</span>

          {delta !== null ? (
            <ClayPill
              tint={isUp ? "var(--chart-2)" : isDown ? "var(--destructive)" : "var(--muted-foreground)"}
              className="mb-3"
            >
              {isUp ? (
                <ArrowUpRight className="size-3.5" aria-hidden />
              ) : isDown ? (
                <ArrowDownRight className="size-3.5" aria-hidden />
              ) : (
                <Minus className="size-3.5" aria-hidden />
              )}
              {formatDelta(delta)}
            </ClayPill>
          ) : null}
        </div>

        <p className="mt-4 text-sm text-muted-foreground">
          {refreshing
            ? "Actualisation…"
            : lastSyncAt
              ? `Mis à jour ${relativeTime(lastSyncAt)}${
                  clockTime(lastSyncAt) ? ` · ${clockTime(lastSyncAt)}` : ""
                }`
              : "En attente de la première synchronisation"}
        </p>
      </div>

      {!average ? (
        <p className="relative mt-5 rounded-2xl bg-card/70 px-4 py-3 text-sm text-muted-foreground">
          Aucune moyenne disponible pour le moment. Dès que tes notes sont accessibles,
          ta moyenne apparaît ici — officielle ou calculée localement.
        </p>
      ) : null}

      <motion.div
        aria-hidden
        className="relative mt-6 h-2 overflow-hidden rounded-full"
        style={{ backgroundColor: "color-mix(in oklab, var(--foreground) 8%, transparent)" }}
      >
        <motion.div
          className="h-full rounded-full"
          style={{ backgroundColor: "color-mix(in oklab, var(--primary) 75%, var(--card))" }}
          initial={false}
          animate={{ width: `${Math.min(100, (display / 20) * 100)}%` }}
          transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
        />
      </motion.div>
    </ClaySurface>
  );
}
