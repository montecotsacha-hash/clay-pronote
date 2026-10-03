/** French relative time formatting (British-French school product language). */

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export function relativeTime(timestamp: number, now = Date.now()): string {
  const diff = now - timestamp;
  if (diff < 45_000) return "à l'instant";
  if (diff < HOUR) {
    const minutes = Math.max(1, Math.round(diff / MINUTE));
    return `il y a ${minutes} min`;
  }
  if (diff < DAY) {
    const hours = Math.round(diff / HOUR);
    return `il y a ${hours} h`;
  }
  const days = Math.round(diff / DAY);
  if (days === 1) return "hier";
  if (days < 7) return `il y a ${days} jours`;
  const weeks = Math.round(days / 7);
  return weeks === 1 ? "il y a 1 semaine" : `il y a ${weeks} semaines`;
}

export function clockTime(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString("fr-FR", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function shortDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00`);
  return date.toLocaleDateString("fr-FR", { day: "numeric", month: "short" });
}

export function longDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00`);
  return date.toLocaleDateString("fr-FR", {
    weekday: "long",
    day: "numeric",
    month: "long",
  });
}

/** Day bucket label used by the grades timeline. */
export function dayBucket(iso: string, now = new Date()): string {
  const date = new Date(`${iso}T00:00:00`);
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diffDays = Math.round((today.getTime() - date.getTime()) / DAY);

  if (diffDays <= 0) return "Aujourd'hui";
  if (diffDays === 1) return "Hier";
  if (diffDays < 7) return "Cette semaine";
  if (diffDays < 30) return "Ce mois-ci";
  return "Plus ancien";
}

export function isWithinDays(iso: string, days: number, now = Date.now()): boolean {
  const date = new Date(`${iso}T00:00:00`).getTime();
  return now - date <= days * DAY;
}

export function monthLabel(iso: string): string {
  const date = new Date(`${iso}T00:00:00`);
  return date.toLocaleDateString("fr-FR", { month: "short" });
}

export function greeting(now = new Date()): string {
  const hour = now.getHours();
  if (hour < 6) return "Bonne nuit";
  if (hour < 12) return "Bonjour";
  if (hour < 18) return "Bon après-midi";
  return "Bonsoir";
}