import { PronoteCompatibilityLayer } from "./data-source";
import { toPronoteError, type PronoteError } from "./errors";
import { logger } from "./logger";
import { SecureCredentialsManager, type StoredCredentials } from "./secure-vault";
import type { PronoteSession } from "./types";

/**
 * SessionManager — owns the PRONOTE session lifecycle.
 *
 * restoreSession()  -> rehydrate from the encrypted vault
 * isSessionValid()  -> cheap expiry check, never a network call
 * refreshSession()  -> try the connector, else surface SessionExpired so the UI
 *                      can ask the user to reconnect while keeping local data
 * clearSession()    -> wipe the vault entry for the account
 */

export interface SessionDeps {
  connector: PronoteCompatibilityLayer;
  vault?: typeof SecureCredentialsManager;
}

export class SessionManager {
  private readonly deps: SessionDeps;
  private readonly vault: typeof SecureCredentialsManager;

  constructor(deps: SessionDeps) {
    this.deps = deps;
    this.vault = deps.vault ?? SecureCredentialsManager;
  }

  async save(
    accountId: string,
    credentials: StoredCredentials,
  ): Promise<void> {
    await this.vault.save(accountId, credentials);
  }

  async load(accountId: string): Promise<StoredCredentials | null> {
    return this.vault.load(accountId);
  }

  async clearSession(accountId: string): Promise<void> {
    await this.vault.clear(accountId);
  }

  async rotateKey(): Promise<void> {
    await this.vault.rotateKey();
  }

  /** A session is valid when a token exists and has not expired. */
  isSessionValid(session: PronoteSession | null, skewMs = 30_000): boolean {
    if (!session?.connected || !session.token) return false;
    return session.expiresAt - skewMs > Date.now();
  }

  /**
   * Rehydrate a session from the vault. Prefers the persistent token; falls
   * back to re-authenticating with the password only if the connector has no
   * token support.
   */
  async restoreSession(accountId: string): Promise<PronoteSession | PronoteError> {
    const stored = await this.vault.load(accountId);
    if (!stored) return toPronoteError("SessionExpired");

    try {
      if (stored.token) {
        const result = await this.deps.connector.restoreSession(
          stored.token,
          stored.schoolUrl,
        );
        return {
          token: result.token,
          expiresAt: result.expiresAt,
          schoolUrl: stored.schoolUrl,
          schoolName: result.schoolName,
          lastSyncAt: Date.now(),
          connected: true,
        };
      }

      if (stored.password) {
        const result = await this.deps.connector.authenticate(
          stored.schoolUrl,
          stored.username,
          stored.password,
        );
        return {
          token: result.token,
          expiresAt: result.expiresAt,
          schoolUrl: stored.schoolUrl,
          schoolName: result.schoolName,
          lastSyncAt: Date.now(),
          connected: true,
        };
      }
    } catch (error) {
      logger.warn("Session restore failed");
      return error as PronoteError;
    }

    return toPronoteError("SessionExpired");
  }

  /** Attempt a refresh; returns null when the session cannot be salvaged. */
  async refreshSession(
    accountId: string,
  ): Promise<PronoteSession | null> {
    const restored = await this.restoreSession(accountId);
    if (restored instanceof Error) return null;
    return restored;
  }
}
