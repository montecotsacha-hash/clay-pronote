import { toPronoteError, PronoteError } from "./errors";
import { logger } from "./logger";
import type {
  PronoteGradeDto,
  PronoteGeneralAverageDto,
  PronoteStudentDto,
  PronoteSubjectAverageDto,
  PronoteSubjectDto,
} from "./mappers";

/**
 * `PronoteDataSource` is the ONLY thing the app knows about PRONOTE.
 *
 * A real connector (open-source library or direct client) implements this
 * interface in `pronote-data-source-real.ts`. Everything above it — repository,
 * use cases, ViewModels, UI, local database — stays untouched.
 *
 * SECURITY: this layer must never bypass an authentication, a CAPTCHA or any
 * security measure. `CaptchaRequired` is a first-class outcome.
 */
export interface PronoteRawPayload {
  student: PronoteStudentDto;
  subjects: PronoteSubjectDto[];
  grades: PronoteGradeDto[];
  subjectAverages: PronoteSubjectAverageDto[];
  generalAverage: PronoteGeneralAverageDto | null;
  periods: { id: string; label: string; current: boolean }[];
}

export interface PronoteAuthResult {
  /** Opaque session token, kept only in the encrypted vault. */
  token: string;
  expiresAt: number;
  /** Student name so we can label the account without a second round-trip. */
  studentName: string;
  schoolName: string;
  payload: PronoteRawPayload;
}

export interface PronoteDataSource {
  readonly id: string;
  /** Human label of the connector strategy currently in use. */
  readonly strategy: string;
  authenticate(params: {
    schoolUrl: string;
    username: string;
    password: string;
  }): Promise<PronoteAuthResult>;
  restoreSession(token: string, schoolUrl: string): Promise<PronoteAuthResult>;
  fetchAll(token: string, schoolUrl: string): Promise<PronoteRawPayload>;
  logout(token: string): Promise<void>;
}

/**
 * PronoteCompatibilityLayer — keeps a connector change from crashing the app.
 * It validates what a connector returned before it reaches the compatibility
 * wrapper, and raises typed errors instead of raw exceptions.
 */
export class PronoteCompatibilityLayer {
  private readonly source: PronoteDataSource;

  constructor(source: PronoteDataSource) {
    this.source = source;
  }

  get strategy(): string {
    return this.source.strategy;
  }

  async authenticate(schoolUrl: string, username: string, password: string) {
    try {
      return await this.source.authenticate({ schoolUrl, username, password });
    } catch (error) {
      throw normalizeConnectorError(error);
    }
  }

  async restoreSession(token: string, schoolUrl: string) {
    try {
      return await this.source.restoreSession(token, schoolUrl);
    } catch (error) {
      throw normalizeConnectorError(error);
    }
  }

  async fetchAll(token: string, schoolUrl: string) {
    try {
      const payload = await this.source.fetchAll(token, schoolUrl);
      return assertPayload(payload);
    } catch (error) {
      throw normalizeConnectorError(error);
    }
  }

  async logout(token: string) {
    try {
      await this.source.logout(token);
    } catch {
      // Logging out must never fail loudly: the local session is cleared anyway.
      logger.warn("Connector logout failed");
    }
  }
}

export function assertPayload(payload: PronoteRawPayload): PronoteRawPayload {
  if (!payload || !payload.student) {
    throw toPronoteError("ParsingError");
  }
  return {
    ...payload,
    subjects: payload.subjects ?? [],
    grades: payload.grades ?? [],
    subjectAverages: payload.subjectAverages ?? [],
    periods: payload.periods ?? [],
  };
}

export function normalizeConnectorError(error: unknown): PronoteError {
  if (error instanceof PronoteError) return error;
  if (error instanceof TypeError) return toPronoteError("NetworkUnavailable", error);
  logger.error("Connector error", { name: (error as Error)?.name });
  return toPronoteError("UnknownError", error);
}