import type { InputHTMLAttributes, ReactNode } from "react";
import { Eye, EyeOff, Info } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";

/** Clay input: a sunken well with a floating label feel. */
export function ClayInput({
  label,
  hint,
  error,
  icon,
  className,
  id,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
  error?: string;
  icon?: ReactNode;
}) {
  const inputId = id ?? `field-${label.replace(/\s+/g, "-").toLowerCase()}`;
  return (
    <div className="flex flex-col gap-2">
      <label
        htmlFor={inputId}
        className="px-1 text-sm font-semibold tracking-tight text-foreground/80"
      >
        {label}
      </label>
      <div
        className={cn(
          "clay-inset flex items-center gap-2.5 rounded-2xl px-4 py-3 transition-colors",
          error && "ring-2 ring-destructive/50",
        )}
      >
        {icon ? <span className="text-muted-foreground">{icon}</span> : null}
        <input
          id={inputId}
          className={cn(
            "w-full min-w-0 bg-transparent text-base font-medium outline-none placeholder:text-muted-foreground/70",
            className,
          )}
          {...props}
        />
      </div>
      {error ? (
        <p className="px-1 text-xs font-medium text-destructive">{error}</p>
      ) : hint ? (
        <p className="flex items-start gap-1.5 px-1 text-xs text-muted-foreground">
          <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden />
          {hint}
        </p>
      ) : null}
    </div>
  );
}

/** Password field: masked by default, with an accessible reveal toggle. */
export function ClayPasswordField({
  label,
  hint,
  error,
  className,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
  error?: string;
}) {
  const [visible, setVisible] = useState(false);
  const inputId = "field-password";

  return (
    <div className="flex flex-col gap-2">
      <label
        htmlFor={inputId}
        className="px-1 text-sm font-semibold tracking-tight text-foreground/80"
      >
        {label}
      </label>
      <div
        className={cn(
          "clay-inset flex items-center gap-2.5 rounded-2xl px-4 py-3",
          error && "ring-2 ring-destructive/50",
        )}
      >
        <input
          id={inputId}
          type={visible ? "text" : "password"}
          autoComplete="current-password"
          className={cn(
            "w-full min-w-0 bg-transparent text-base font-medium outline-none placeholder:text-muted-foreground/70",
            className,
          )}
          {...props}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? "Masquer le mot de passe" : "Afficher le mot de passe"}
          className="clay-pressable rounded-xl p-1 text-muted-foreground hover:text-foreground"
        >
          {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>
      {error ? (
        <p className="px-1 text-xs font-medium text-destructive">{error}</p>
      ) : hint ? (
        <p className="px-1 text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}

/** Clay switch: a squishy track with an inflated thumb. */
export function ClaySwitch({
  checked,
  onCheckedChange,
  label,
  description,
  id,
}: {
  checked: boolean;
  onCheckedChange: (value: boolean) => void;
  label: string;
  description?: string;
  id?: string;
}) {
  const switchId = id ?? `switch-${label.replace(/\s+/g, "-").toLowerCase()}`;
  return (
    <div className="flex items-start justify-between gap-4 py-3">
      <div className="min-w-0">
        <label
          htmlFor={switchId}
          className="block text-sm font-semibold tracking-tight"
        >
          {label}
        </label>
        {description ? (
          <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
            {description}
          </p>
        ) : null}
      </div>
      <button
        id={switchId}
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={() => onCheckedChange(!checked)}
        className={cn(
          "clay-inset relative h-8 w-14 shrink-0 rounded-full transition-colors",
          checked && "clay-glow",
        )}
        style={
          checked
            ? { backgroundColor: "color-mix(in oklab, var(--primary) 72%, var(--card))" }
            : undefined
        }
      >
        <span
          className={cn(
            "clay-sm absolute top-1 size-6 rounded-full bg-card transition-transform duration-200",
            checked ? "translate-x-7" : "translate-x-1",
          )}
        />
      </button>
    </div>
  );
}

/** Segmented control: a row of clay pills inside a sunken well. */
export function ClaySegmented<T extends string>({
  options,
  value,
  onChange,
  label,
  className,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  label: string;
  className?: string;
}) {
  return (
    <div
      role="tablist"
      aria-label={label}
      className={cn("clay-inset flex gap-1 rounded-2xl p-1.5", className)}
    >
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value}
            role="tab"
            aria-selected={active}
            type="button"
            onClick={() => onChange(option.value)}
            className={cn(
              "clay-pressable flex-1 rounded-xl px-3 py-2 text-xs font-semibold tracking-tight transition-all",
              active
                ? "clay-sm text-primary-foreground"
                : "text-muted-foreground hover:text-foreground",
            )}
            style={
              active
                ? {
                    backgroundImage:
                      "linear-gradient(150deg, color-mix(in oklab, var(--primary) 90%, white 10%), var(--primary))",
                  }
                : undefined
            }
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
