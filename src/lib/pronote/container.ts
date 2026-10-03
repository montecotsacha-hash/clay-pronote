import { PronoteCompatibilityLayer, type PronoteDataSource } from "./data-source";
import { FakePronoteDataSource } from "./fake-data-source";
import { LocalDataSource } from "./local-data-source";
import { PronoteRepositoryImpl } from "./repository";
import { SessionManager } from "./session-manager";
import { SyncManager } from "./sync-manager";
import {
  CalculateGeneralAverageUseCase,
  DeleteLocalDataUseCase,
  GetAveragesUseCase,
  GetCachedDataUseCase,
  GetGradesUseCase,
  GetStudentUseCase,
  GetSubjectRowsUseCase,
  GetSubjectsUseCase,
  LoginUseCase,
  LogoutUseCase,
  PreferencesUseCase,
  RefreshSessionUseCase,
  SyncPronoteUseCase,
} from "./use-cases";

/**
 * Composition root.
 *
 * Swapping the PRONOTE connector is a one-line change here: nothing else in the
 * app knows which implementation is behind `PronoteDataSource`.
 */
export interface PronoteContainer {
  dataSource: PronoteDataSource;
  repository: PronoteRepositoryImpl;
  sessions: SessionManager;
  syncManager: SyncManager;
  useCases: {
    login: LoginUseCase;
    logout: LogoutUseCase;
    getCachedData: GetCachedDataUseCase;
    sync: SyncPronoteUseCase;
    refreshSession: RefreshSessionUseCase;
    deleteLocalData: DeleteLocalDataUseCase;
    preferences: PreferencesUseCase;
    getStudent: GetStudentUseCase;
    getSubjects: GetSubjectsUseCase;
    getGrades: GetGradesUseCase;
    getAverages: GetAveragesUseCase;
    getSubjectRows: GetSubjectRowsUseCase;
    calculateGeneralAverage: CalculateGeneralAverageUseCase;
  };
}

export function createContainer(options?: {
  dataSource?: PronoteDataSource;
  isOnline?: () => boolean;
}): PronoteContainer {
  const dataSource = options?.dataSource ?? new FakePronoteDataSource();
  const connector = new PronoteCompatibilityLayer(dataSource);
  const sessions = new SessionManager({ connector });
  const repository = new PronoteRepositoryImpl(connector, sessions, LocalDataSource);

  const syncManager = new SyncManager({
    repository,
    isOnline:
      options?.isOnline ??
      (() => (typeof navigator === "undefined" ? true : navigator.onLine)),
  });

  return {
    dataSource,
    repository,
    sessions,
    syncManager,
    useCases: {
      login: new LoginUseCase(repository),
      logout: new LogoutUseCase(repository),
      getCachedData: new GetCachedDataUseCase(repository),
      sync: new SyncPronoteUseCase(repository),
      refreshSession: new RefreshSessionUseCase(repository),
      deleteLocalData: new DeleteLocalDataUseCase(repository),
      preferences: new PreferencesUseCase(repository),
      getStudent: new GetStudentUseCase(),
      getSubjects: new GetSubjectsUseCase(),
      getGrades: new GetGradesUseCase(),
      getAverages: new GetAveragesUseCase(),
      getSubjectRows: new GetSubjectRowsUseCase(),
      calculateGeneralAverage: new CalculateGeneralAverageUseCase(),
    },
  };
}

/** Shared singleton for the app. */
export const container = createContainer();