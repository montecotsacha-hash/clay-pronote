package com.pronote.clay.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pronote.clay.AppContainer
import com.pronote.clay.core.PronoteError
import com.pronote.clay.core.PronoteResult
import com.pronote.clay.domain.AverageSummary
import com.pronote.clay.domain.Preferences
import com.pronote.clay.domain.SchoolSnapshot
import com.pronote.clay.domain.SubjectRow
import com.pronote.clay.notifications.LocalNotifications
import com.pronote.clay.notifications.PendingNotification
import com.pronote.clay.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Explicit UI state, mirroring the product spec:
 * Loading | Success | Refreshing | Offline | Error | Unauthenticated
 */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Unauthenticated : HomeUiState

    data class Success(
        val snapshot: SchoolSnapshot,
        val average: AverageSummary?,
        val subjects: List<SubjectRow>,
        val refreshing: Boolean,
    ) : HomeUiState

    data class Offline(
        val snapshot: SchoolSnapshot?,
        val average: AverageSummary?,
        val subjects: List<SubjectRow> = emptyList(),
    ) : HomeUiState

    /**
     * Sync failed but cached data exists: the dashboard stays usable and the
     * problem is shown as a banner. Not an Error state — Error only when
     * there is nothing to show.
     */
    data class SyncIssue(
        val snapshot: SchoolSnapshot,
        val average: AverageSummary?,
        val subjects: List<SubjectRow> = emptyList(),
        val message: String,
        val hint: String? = null,
    ) : HomeUiState

    data class Error(
        val message: String,
        val hint: String?,
        val snapshot: SchoolSnapshot?,
        val subjects: List<SubjectRow> = emptyList(),
    ) : HomeUiState
}

/**
 * Login form draft. It lives in the ViewModel because the login screen is
 * unmounted while the attempt runs: a failed attempt must never wipe what the
 * user typed — only the offending value is cleared.
 */
data class LoginDraft(
    val schoolUrl: String = "",
    val username: String = "",
    val password: String = "",
)

/** Transient events the UI must act on (notifications, toasts). */
sealed interface HomeEvent {
    data class Notify(val notification: PendingNotification) : HomeEvent
    data class Message(val text: String) : HomeEvent
}

/**
 * HomeViewModel — holds no business logic: every decision goes through a use
 * case or the SyncManager. It renders cached data first, then refreshes
 * silently in the background.
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<HomeEvent?>(null)
    val events: StateFlow<HomeEvent?> = _events.asStateFlow()

    private val _preferences = MutableStateFlow(container.preferences.load())
    val preferences: StateFlow<Preferences> = _preferences.asStateFlow()

    private val _online = MutableStateFlow(container.networkMonitor.isOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _loginDraft = MutableStateFlow(LoginDraft())
    val loginDraft: StateFlow<LoginDraft> = _loginDraft.asStateFlow()

    fun updateLoginSchoolUrl(value: String) {
        _loginDraft.value = _loginDraft.value.copy(schoolUrl = value)
    }

    fun updateLoginUsername(value: String) {
        _loginDraft.value = _loginDraft.value.copy(username = value)
    }

    fun updateLoginPassword(value: String) {
        _loginDraft.value = _loginDraft.value.copy(password = value)
    }

    private var sessionValid = false

    /** Human label of the connector strategy, shown in the settings sheet. */
    val connectorLabel: String get() = container.dataSource.strategy

    init {
        viewModelScope.launch {
            container.networkMonitor.online.collect { isOnline ->
                _online.value = isOnline
                applyCurrentState(isOnline)
            }
        }
        loadFromCacheThenRefresh()
    }

    /** Step 1: show local data instantly. Step 2: refresh silently. */
    private fun loadFromCacheThenRefresh() {
        val accountId = container.activeAccountId()
        if (accountId == null) {
            _state.value = HomeUiState.Unauthenticated
            return
        }

        val cached = container.getCachedData.execute(accountId)
        if (cached == null) {
            _state.value = HomeUiState.Unauthenticated
            return
        }

        _state.value = buildSuccess(cached, refreshing = true)

        viewModelScope.launch {
            // Session restore is bounded so the UI never blocks on the network.
            val restored = container.refreshSession.execute(accountId)
            sessionValid = restored != null
            if (restored != null) {
                sync(force = false)
            } else {
                applyCurrentState(container.networkMonitor.isOnline())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { sync(force = true) }
    }

    fun markSessionInvalid() {
        sessionValid = false
        applyCurrentState(container.networkMonitor.isOnline())
    }

    private suspend fun sync(force: Boolean) {
        val accountId = container.activeAccountId() ?: return
        setRefreshing(true)

        val result = container.syncManager.sync(accountId, force = force)

        when {
            result.ok && result.snapshot != null -> {
                _state.value = buildSuccess(result.snapshot, refreshing = false)
                emitNotifications(result)
            }
            result.skipped == SyncManager.SkipReason.OFFLINE -> {
                applyCurrentState(isOnline = false)
            }
            result.error != null -> {
                val cached = container.getCachedData.execute(accountId)
                if (cached != null) {
                    // With local data available, a failed background sync is
                    // never a login failure: keep the dashboard usable and
                    // report the problem as a banner the next retry can clear.
                    _state.value = HomeUiState.SyncIssue(
                        snapshot = cached,
                        average = container.calculateGeneralAverage.execute(cached),
                        subjects = container.getSubjectRows.execute(cached),
                        message = result.error,
                        hint = result.errorHint,
                    )
                } else {
                    _state.value = HomeUiState.Error(
                        message = result.error,
                        hint = result.errorHint ?: PronoteError.NetworkUnavailable.hint,
                        snapshot = null,
                        subjects = emptyList(),
                    )
                }
            }
            else -> setRefreshing(false)
        }
    }

    private fun emitNotifications(result: SyncManager.SyncResult) {
        val prefs = _preferences.value
        val snapshot = result.snapshot ?: return

        if (prefs.notifyNewGrades && result.newGrades.isNotEmpty()) {
            val grade = result.newGrades.first()
            val subject = snapshot.subjects.find { it.id == grade.subjectId }
            _events.value = HomeEvent.Notify(
                PendingNotification(
                    title = "Nouvelle note",
                    body = LocalNotifications.newGradeNotification(grade, subject),
                ),
            )
            return
        }

        val previous = result.previousAverage
        val next = result.nextAverage
        if (prefs.notifyAverageChange && previous != null && next != null &&
            kotlin.math.abs(next - previous) >= 0.05
        ) {
            _events.value = HomeEvent.Notify(
                PendingNotification(
                    title = "Ta moyenne a changé",
                    body = LocalNotifications.averageChangeNotification(next, previous),
                ),
            )
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    private fun setRefreshing(refreshing: Boolean) {
        val current = _state.value
        if (current is HomeUiState.Success) {
            _state.value = current.copy(refreshing = refreshing)
        }
    }

    private fun applyCurrentState(isOnline: Boolean) {
        val accountId = container.activeAccountId()
        val snapshot = accountId?.let { container.getCachedData.execute(it) }

        _state.value = when {
            snapshot == null -> HomeUiState.Unauthenticated
            !isOnline -> HomeUiState.Offline(
                snapshot = snapshot,
                average = container.calculateGeneralAverage.execute(snapshot),
                subjects = container.getSubjectRows.execute(snapshot),
            )
            else -> buildSuccess(snapshot, refreshing = false)
        }
    }

    private fun buildSuccess(snapshot: SchoolSnapshot, refreshing: Boolean): HomeUiState.Success =
        HomeUiState.Success(
            snapshot = snapshot,
            average = container.calculateGeneralAverage.execute(snapshot),
            subjects = container.getSubjectRows.execute(snapshot),
            refreshing = refreshing,
        )

    // ---- actions ----------------------------------------------------------

    fun login(
        schoolUrl: String,
        username: String,
        password: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            val accountId = accountIdFor(username, schoolUrl)

            when (val result = container.login.execute(accountId, schoolUrl, username, password)) {
                is PronoteResult.Success -> {
                    container.setActiveAccount(accountId)
                    container.syncManager.reset()
                    sessionValid = true
                    val snapshot = container.getCachedData.execute(accountId)
                    _state.value = if (snapshot != null) {
                        buildSuccess(snapshot, refreshing = false)
                    } else {
                        HomeUiState.Unauthenticated
                    }
                    onSuccess()
                }
                is PronoteResult.Failure -> {
                    // Only clear the value that caused the failure; the other
                    // entries (address, identifiants) stay filled in.
                    _loginDraft.value = when (result.error) {
                        is PronoteError.InvalidPronoteUrl ->
                            _loginDraft.value.copy(schoolUrl = "")
                        is PronoteError.InvalidCredentials ->
                            _loginDraft.value.copy(password = "")
                        else -> _loginDraft.value
                    }
                    _state.value = HomeUiState.Error(
                        message = result.error.userMessage,
                        hint = result.error.hint,
                        snapshot = null,
                    )
                }
            }
        }
    }

    /** Stable local account id, derived from the establishment and login. */
    private fun accountIdFor(username: String, schoolUrl: String): String =
        "acct-" + Integer.toHexString("$schoolUrl|$username".hashCode())

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            container.activeAccountId()?.let { container.logout.execute(it) }
            container.setActiveAccount(null)
            sessionValid = false
            _state.value = HomeUiState.Unauthenticated
            onDone()
        }
    }

    fun deleteLocalData(onDone: () -> Unit) {
        viewModelScope.launch {
            container.activeAccountId()?.let { container.deleteLocalData.execute(it) }
            container.setActiveAccount(null)
            container.preferences.clear()
            _preferences.value = container.preferences.load()
            sessionValid = false
            _state.value = HomeUiState.Unauthenticated
            onDone()
        }
    }

    fun updatePreferences(preferences: Preferences) {
        container.preferences.save(preferences)
        _preferences.value = preferences
    }

    fun clearSession() {
        viewModelScope.launch {
            container.activeAccountId()?.let { container.logout.execute(it) }
            sessionValid = false
        }
    }
}
