import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * ClaySurface — the base plush panel used across the whole app.
 *
 * A claymorphism surface is a matte pastel block with two shadows: a soft light
 * from the top-left and a deeper tinted shadow to the bottom-right, so it reads
 * as an inflated, tactile object rather than a flat card.
 */

type ClayDepth = "sm" | "md" | "lg";

const DEPTH_CLASS: Record<ClayDepth, string> = {
  sm: "clay-sm",
  md: "clay",
  lg: "clay-lg",
};

export function ClaySurface({
  className,
  depth = "md",
  pressable = false,
  tint,
  children,
  ...props
}: HTMLAttributes<HTMLDivElement> & {
  depth?: ClayDepth;
  pressable?: boolean;
  /** Optional pastel tint layered on the card colour. */
  tint?: string;
  children?: ReactNode;
}) {
  return (
    <div
      className={cn(
        "relative rounded-3xl border border-white/40 dark:border-white/5",
        DEPTH_CLASS[depth],
        pressable && "clay-pressable",
        className,
      )}
      style={
        tint
          ? {
              backgroundImage: `linear-gradient(150deg, color-mix(in oklab, ${tint} 16%, var(--card)), var(--card))`,
            }
          : undefined
      }
      {...props}
    >
      {children}
    </div>
  );
}

/** Sunken clay well — for inputs, toggles and progress tracks. */
export function ClayWell({
  className,
  children,
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn("clay-inset rounded-2xl", className)} {...props}>
      {children}
    </div>
  );
}

/** A small pastel clay pill (badges, chips, source labels). */
export function ClayPill({
  className,
  tint = "var(--primary)",
  children,
  ...props
}: HTMLAttributes<HTMLSpanElement> & { tint?: string }) {
  return (
    <span
      className={cn(
        "clay-sm inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold tracking-tight",
        className,
      )}
      style={{
        backgroundColor: `color-mix(in oklab, ${tint} 26%, var(--card))`,
        color: `color-mix(in oklab, ${tint} 72%, var(--foreground))`,
      }}
      {...props}
    >
      {children}
    </span>
  );
}

/** Section heading with a small clay blob accent. */
export function ClaySectionTitle({
  children,
  action,
  className,
}: {
  children: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex items-end justify-between gap-4 px-1", className)}>
      <h2 className="flex items-center gap-2.5 text-lg font-bold tracking-tight">
        <span
          aria-hidden
          className="clay-sm size-3 rounded-full"
          style={{ backgroundColor: "color-mix(in oklab, var(--primary) 55%, var(--card))" }}
        />
        {children}
      </h2>
      {action}
    </div>
  );
}
