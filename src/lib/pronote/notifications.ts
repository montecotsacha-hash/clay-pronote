import { formatAverage, formatGrade } from "./average";
import type { Grade, Subject } from "./types";

/**
 * Local notifications — generated on-device after a sync.
 *
 * No school data is ever sent to a third-party service just to produce a
 * notification, and nothing here runs when the user disabled the category.
 */

export function notificationsSupported(): boolean {
  return typeof window !== "undefined" && "Notification" in window;
}

export function notificationPermission(): NotificationPermission {
  if (!notificationsSupported()) return "denied";
  return Notification.permission;
}

export async function requestNotificationPermission(): Promise<NotificationPermission> {
  if (!notificationsSupported()) return "denied";
  if (Notification.permission !== "default") return Notification.permission;
  try {
    return await Notification.requestPermission();
  } catch {
    return "denied";
  }
}

export function notify(title: string, body: string): boolean {
  if (!notificationsSupported() || Notification.permission !== "granted") return false;
  try {
    new Notification(title, { body, tag: "pronote-clay", silent: false });
    return true;
  } catch {
    return false;
  }
}

export function newGradeNotification(grade: Grade, subject: Subject | undefined): string {
  const name = subject?.name ?? "une matière";
  return `Une nouvelle note de ${formatGrade(grade.value, grade.maxValue)} en ${name.toLowerCase()} vient d'être ajoutée.`;
}

export function averageChangeNotification(next: number, previous: number): string {
  const direction = next >= previous ? "est maintenant de" : "est passée à";
  return `Ta moyenne générale ${direction} ${formatAverage(next)}/20.`;
}
