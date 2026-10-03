import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

/**
 * ThemeProvider — light / dark / system plus a Material-You-style dynamic accent.
 *
 * The Claymorphism palette lives in `index.css`; this provider only toggles the
 * `dark` class and an optional accent override that reshades the clay glows.
 */

export type ThemeMode = "system" | "light" | "dark";

interface ThemeContextValue {
  mode: ThemeMode;
  resolved: "light" | "dark";
  setMode: (mode: ThemeMode) => void;
  dynamicColor: boolean;
  setDynamicColor: (value: boolean) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

const STORAGE_KEY = "pronote-clay:theme";

function readStored(): { mode: ThemeMode; dynamicColor: boolean } {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { mode: "system", dynamicColor: true };
    const parsed = JSON.parse(raw) as { mode?: ThemeMode; dynamicColor?: boolean };
    return {
      mode: parsed.mode ?? "system",
      dynamicColor: parsed.dynamicColor ?? true,
    };
  } catch {
    return { mode: "system", dynamicColor: true };
  }
}

function systemPrefersDark(): boolean {
  if (typeof window === "undefined" || !window.matchMedia) return false;
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const stored = useMemo(() => readStored(), []);
  const [mode, setModeState] = useState<ThemeMode>(stored.mode);
  const [dynamicColor, setDynamicColorState] = useState(stored.dynamicColor);
  const [systemDark, setSystemDark] = useState(systemPrefersDark);

  useEffect(() => {
    if (!window.matchMedia) return;
    const query = window.matchMedia("(prefers-color-scheme: dark)");
    const listener = (event: MediaQueryListEvent) => setSystemDark(event.matches);
    query.addEventListener("change", listener);
    return () => query.removeEventListener("change", listener);
  }, []);

  const resolved: "light" | "dark" =
    mode === "system" ? (systemDark ? "dark" : "light") : mode;

  useEffect(() => {
    const root = document.documentElement;
    root.classList.toggle("dark", resolved === "dark");
    root.style.colorScheme = resolved;
  }, [resolved]);

  useEffect(() => {
    const root = document.documentElement;
    if (dynamicColor) root.removeAttribute("data-flat-accent");
    else root.setAttribute("data-flat-accent", "true");
  }, [dynamicColor]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ mode, dynamicColor }));
    } catch {
      /* non-fatal */
    }
  }, [mode, dynamicColor]);

  const setMode = useCallback((next: ThemeMode) => setModeState(next), []);
  const setDynamicColor = useCallback((next: boolean) => setDynamicColorState(next), []);

  const value = useMemo(
    () => ({ mode, resolved, setMode, dynamicColor, setDynamicColor }),
    [mode, resolved, setMode, dynamicColor, setDynamicColor],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("useTheme must be used inside ThemeProvider");
  return context;
}
