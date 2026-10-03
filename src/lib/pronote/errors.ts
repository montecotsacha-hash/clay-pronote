/**
 * Typed errors. The UI turns these into human sentences — a stack trace never
 * reaches the user.
 */

export type PronoteErrorCode =
  | "InvalidCredentials"
  | "InvalidPronoteUrl"
  | "NetworkUnavailable"
  | "SessionExpired"
  | "PronoteUnavailable"
  | "CaptchaRequired"
  | "ServerError"
  | "ParsingError"
  | "IncompatibleConnector"
  | "UnknownError";

export class PronoteError extends Error {
  readonly code: PronoteErrorCode;
  /** Message safe to show to the user (French, product language). */
  readonly userMessage: string;
  /** Optional follow-up hint. */
  readonly hint?: string;

  constructor(
    code: PronoteErrorCode,
    userMessage: string,
    options?: { hint?: string; cause?: unknown },
  ) {
    super(code);
    this.name = "PronoteError";
    this.code = code;
    this.userMessage = userMessage;
    this.hint = options?.hint;
    if (options?.cause !== undefined) {
      (this as { cause?: unknown }).cause = options.cause;
    }
  }
}

const MESSAGES: Record<PronoteErrorCode, { message: string; hint?: string }> = {
  InvalidCredentials: {
    message: "Impossible de se connecter à PRONOTE.",
    hint: "Vérifie ton identifiant et ton mot de passe.",
  },
  InvalidPronoteUrl: {
    message: "L'adresse de ton établissement n'est pas valide.",
    hint: "Exemple : https://0123456a.index-education.net/pronote/",
  },
  NetworkUnavailable: {
    message: "Aucune connexion internet.",
    hint: "Tes dernières données restent consultables hors ligne.",
  },
  SessionExpired: {
    message: "Ta session PRONOTE a expiré.",
    hint: "Reconnecte-toi pour repartir sur des données à jour.",
  },
  PronoteUnavailable: {
    message: "PRONOTE ne répond pas pour le moment.",
    hint: "Réessaie dans quelques minutes.",
  },
  CaptchaRequired: {
    message: "Cet établissement demande une vérification supplémentaire.",
    hint: "Ouvre PRONOTE dans un navigateur pour te connecter une fois, puis réessaie.",
  },
  ServerError: {
    message: "Le serveur de l'établissement a renvoyé une erreur.",
    hint: "Ce n'est pas de ton côté — réessaie plus tard.",
  },
  ParsingError: {
    message: "Les données reçues n'ont pas pu être interprétées.",
    hint: "Une mise à jour du connecteur est peut-être nécessaire.",
  },
  IncompatibleConnector: {
    message: "PRONOTE a modifié son système de connexion.",
    hint: "Une mise à jour du connecteur est nécessaire. Tes données locales restent disponibles.",
  },
  UnknownError: {
    message: "Une erreur inattendue est survenue.",
  },
};

export function toPronoteError(
  code: PronoteErrorCode,
  cause?: unknown,
): PronoteError {
  const { message, hint } = MESSAGES[code];
  return new PronoteError(code, message, { hint, cause });
}

export function userMessageFor(error: unknown): string {
  if (error instanceof PronoteError) return error.userMessage;
  return MESSAGES.UnknownError.message;
}

export function hintFor(error: unknown): string | undefined {
  if (error instanceof PronoteError) return error.hint;
  return undefined;
}
