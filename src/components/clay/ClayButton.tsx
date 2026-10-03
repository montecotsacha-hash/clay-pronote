import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

type ClayVariant = "primary" | "secondary" | "ghost" | "danger";
type ClaySize = "sm" | "md" | "lg";

const VARIANTS: Record<ClayVariant, string> = {
  primary: "text-primary-foreground",
  secondary: "text-secondary-foreground",
  ghost: "text-foreground",
  danger: "text-white",
};

const SIZES: Record<ClaySize, string> = {
  sm: "h-10 px-4 text-sm rounded-2xl",
  md: "h-12 px-6 text-sm rounded-2xl",
  lg: "h-14 px-7 text-base rounded-3xl",
};

export function ClayButton({
  className,
  variant = "primary",
  size = "md",
  loading = false,
  icon,
  children,
  disabled,
  style,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ClayVariant;
  size?: ClaySize;
  loading?: boolean;
  icon?: ReactNode;
}) {
  const background =
    variant === "primary"
      ? "linear-gradient(150deg, color-mix(in oklab, var(--primary) 88%, white 12%), var(--primary))"
      : variant === "danger"
        ? "linear-gradient(150deg, color-mix(in oklab, var(--destructive) 88%, white 10%), var(--destructive))"
        : variant === "secondary"
          ? "linear-gradient(150deg, color-mix(in oklab, var(--secondary) 92%, white 8%), var(--secondary))"
          : "transparent";

  return (
    <button
      className={cn(
        "clay-pressable clay inline-flex items-center justify-center gap-2 font-semibold tracking-tight",
        "border border-white/40 dark:border-white/10",
        VARIANTS[variant],
        SIZES[size],
        variant === "ghost" && "clay-sm",
        disabled && "pointer-events-none opacity-60",
        className,
      )}
      style={{ backgroundImage: background, ...style }}
      disabled={disabled || loading}
      {...props}
    >
      {loading ? <Loader2 className="size-4 animate-spin" aria-hidden /> : icon}
      {children}
    </button>
  );
}

/** Small round clay icon button (nav, refresh, close). */
export function ClayIconButton({
  className,
  label,
  loading = false,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { label: string; loading?: boolean }) {
  return (
    <button
      aria-label={label}
      title={label}
      className={cn(
        "clay-sm clay-pressable inline-flex size-11 items-center justify-center rounded-2xl",
        "border border-white/40 text-foreground dark:border-white/10",
        className,
      )}
      {...props}
    >
      {loading ? <Loader2 className="size-4 animate-spin" aria-hidden /> : children}
    </button>
  );
}
