import { motion } from "framer-motion";
import {
  BarChart3,
  BookOpen,
  GraduationCap,
  LayoutGrid,
  ListChecks,
  Settings as SettingsIcon,
  WifiOff,
} from "lucide-react";
import type { ReactNode } from "react";
import { NavLink, useNavigate } from "react-router";
import { ClayPill, ClaySurface } from "./ClaySurface";
import { usePronote } from "@/lib/pronote/store";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  { to: "/app", label: "Tableau de bord", shortLabel: "Tableau", icon: LayoutGrid, end: true },
  { to: "/app/subjects", label: "Matières", shortLabel: "Matières", icon: BookOpen },
  { to: "/app/grades", label: "Notes", shortLabel: "Notes", icon: ListChecks },
  { to: "/app/history", label: "Historique", shortLabel: "Historique", icon: BarChart3 },
  { to: "/app/settings", label: "Réglages", shortLabel: "Réglages", icon: SettingsIcon },
];

/**
 * AppShell — sidebar on desktop, clay bottom bar on mobile.
 * Always shows a discreet offline banner when the network is gone so the user
 * understands stale data is expected, never a blank screen.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const { online, snapshot, syncing } = usePronote();
  const navigate = useNavigate();
  const student = snapshot?.student;

  return (
    <div className="min-h-screen">
      <div className="mx-auto flex w-full max-w-6xl gap-6 px-4 pb-28 pt-6 sm:px-6 lg:pb-10">
        {/* Desktop sidebar */}
        <aside className="sticky top-6 hidden h-fit w-60 shrink-0 lg:block">
          <ClaySurface depth="lg" className="p-4">
            <button
              type="button"
              onClick={() => navigate("/")}
              className="clay-pressable mb-5 flex w-full items-center gap-3 rounded-2xl px-2 py-2 text-left"
            >
              <span
                className="clay-sm flex size-10 items-center justify-center rounded-2xl"
                style={{
                  backgroundColor: "color-mix(in oklab, var(--primary) 30%, var(--card))",
                }}
              >
                <GraduationCap className="size-5 text-foreground" aria-hidden />
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-bold tracking-tight">Pronote</span>
                <span className="block truncate text-xs text-muted-foreground">
                  {student ? `${student.firstName} ${student.lastName}`.trim() : "Aucun élève lié"}
                </span>
              </span>
            </button>

            <nav className="flex flex-col gap-1.5" aria-label="Navigation principale">
              {NAV_ITEMS.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} className="block">
                  {({ isActive }) => (
                    <span
                      className={cn(
                        "clay-pressable flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-semibold tracking-tight transition-all",
                        isActive
                          ? "clay-sm text-primary-foreground"
                          : "text-muted-foreground hover:text-foreground",
                      )}
                      style={
                        isActive
                          ? {
                              backgroundImage:
                                "linear-gradient(150deg, color-mix(in oklab, var(--primary) 90%, white 10%), var(--primary))",
                            }
                          : undefined
                      }
                    >
                      <item.icon className="size-4" aria-hidden />
                      {item.label}
                    </span>
                  )}
                </NavLink>
              ))}
            </nav>

            <div className="mt-5 border-t border-border/60 pt-4">
              {student ? (
                <div className="px-2">
                  <p className="text-xs font-semibold text-muted-foreground">Classe</p>
                  <p className="text-sm font-bold tracking-tight">{student.className || "—"}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{student.schoolName}</p>
                </div>
              ) : null}
              {syncing ? (
                <ClayPill tint="var(--chart-3)" className="mt-3 ml-2">
                  Synchronisation…
                </ClayPill>
              ) : null}
            </div>
          </ClaySurface>
        </aside>

        <main className="min-w-0 flex-1">
          {!online ? (
            <motion.div
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              className="mb-4"
            >
              <ClaySurface
                depth="sm"
                className="flex items-center gap-3 px-4 py-3"
                tint="var(--chart-3)"
                role="status"
              >
                <WifiOff className="size-4 shrink-0" aria-hidden />
                <p className="text-sm font-medium">
                  Mode hors connexion — tes dernières données restent consultables.
                </p>
              </ClaySurface>
            </motion.div>
          ) : null}
          {children}
        </main>
      </div>

      {/* Mobile bottom nav */}
      <nav
        aria-label="Navigation principale"
        className="fixed inset-x-0 bottom-0 z-40 px-3 pb-3 lg:hidden"
      >
        <ClaySurface depth="lg" className="flex items-center justify-between gap-1 p-2">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className="flex min-w-0 flex-1 flex-col items-center gap-1"
            >
              {({ isActive }) => (
                <>
                  <span
                    className={cn(
                      "clay-pressable flex size-11 items-center justify-center rounded-2xl transition-all",
                      isActive ? "clay-sm" : "",
                    )}
                    style={
                      isActive
                        ? {
                            backgroundColor:
                              "color-mix(in oklab, var(--primary) 32%, var(--card))",
                          }
                        : undefined
                    }
                  >
                    <item.icon
                      className={cn("size-5", isActive ? "text-foreground" : "text-muted-foreground")}
                      aria-hidden
                    />
                  </span>
                  <span
                    className={cn(
                      "truncate text-[10px] font-semibold tracking-tight",
                      isActive ? "text-foreground" : "text-muted-foreground",
                    )}
                  >
                    {item.shortLabel}
                  </span>
                </>
              )}
            </NavLink>
          ))}
        </ClaySurface>
      </nav>
    </div>
  );
}
