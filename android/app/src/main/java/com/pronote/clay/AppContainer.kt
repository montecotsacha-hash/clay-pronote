package com.pronote.clay

import android.content.Context
import com.pronote.clay.core.NetworkMonitor
import com.pronote.clay.data.PronoteRepository
import com.pronote.clay.data.PronoteRepositoryImpl
import com.pronote.clay.data.SessionManager
import com.pronote.clay.data.local.LocalDataSource
import com.pronote.clay.data.local.PreferencesStore
import com.pronote.clay.data.remote.PronoteDirectDataSource
import com.pronote.clay.data.remote.PronoteCompatibilityLayer
import com.pronote.clay.data.remote.PronoteDataSource
import com.pronote.clay.data.security.SecureCredentialsManager
import com.pronote.clay.domain.CalculateGeneralAverageUseCase
import com.pronote.clay.domain.DeleteLocalDataUseCase
import com.pronote.clay.domain.GetAveragesUseCase
import com.pronote.clay.domain.GetCachedDataUseCase
import com.pronote.clay.domain.GetGradesUseCase
import com.pronote.clay.domain.GetStudentUseCase
import com.pronote.clay.domain.GetSubjectRowsUseCase
import com.pronote.clay.domain.GetSubjectsUseCase
import com.pronote.clay.domain.GetTimetableUseCase
import com.pronote.clay.domain.LoginUseCase
import com.pronote.clay.domain.LogoutUseCase
import com.pronote.clay.domain.RefreshSessionUseCase
import com.pronote.clay.domain.SyncPronoteUseCase
import com.pronote.clay.sync.SyncManager

/**
 * Composition root.
 *
 * Swapping the Pronote connector is a one-line change here: nothing else in the
 * app knows which implementation is behind [PronoteDataSource].
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val dataSource: PronoteDataSource = PronoteDirectDataSource()

    val networkMonitor = NetworkMonitor(appContext)

    val preferences = PreferencesStore(appContext)

    private val local = LocalDataSource(appContext)
    private val vault = SecureCredentialsManager(appContext)
    private val connector = PronoteCompatibilityLayer(dataSource)
    private val sessions = SessionManager(connector, vault)

    val repository: PronoteRepository = PronoteRepositoryImpl(connector, sessions, local) {
        // Chaque nouvelle donnée est répercutée sur les widgets d'accueil.
        com.pronote.clay.widgets.ArdoiseWidgetProvider.updateAllAsync(appContext)
    }

    val syncManager = SyncManager(repository) { networkMonitor.isOnline() }

    // ---- use cases --------------------------------------------------------

    val login = LoginUseCase(repository)
    val logout = LogoutUseCase(repository)
    val getCachedData = GetCachedDataUseCase(repository)
    val syncPronote = SyncPronoteUseCase(repository)
    val refreshSession = RefreshSessionUseCase(repository)
    val deleteLocalData = DeleteLocalDataUseCase(repository)
    val getStudent = GetStudentUseCase()
    val getSubjects = GetSubjectsUseCase()
    val getGrades = GetGradesUseCase()
    val getAverages = GetAveragesUseCase()
    val getSubjectRows = GetSubjectRowsUseCase()
    val getTimetable = GetTimetableUseCase(repository)
    val calculateGeneralAverage = CalculateGeneralAverageUseCase()

    /**
     * The single account on this device. A multi-profile build would replace this
     * with a list of accounts, but the per-account scoping already isolates
     * storage, cache and session per account.
     */
    fun activeAccountId(): String? = activeAccount

    fun setActiveAccount(accountId: String?) {
        activeAccount = accountId
        appContext.getSharedPreferences("pronote_accounts", Context.MODE_PRIVATE)
            .edit()
            .putString("active", accountId)
            .apply()
    }

    private var activeAccount: String? =
        appContext.getSharedPreferences("pronote_accounts", Context.MODE_PRIVATE)
            .getString("active", null)
}