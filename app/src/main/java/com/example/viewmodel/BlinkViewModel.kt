package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BlinkRepository
import com.example.domain.AccountStatus
import com.example.domain.AdminAuditLog
import com.example.domain.Announcement
import com.example.domain.BlinkValidators
import com.example.domain.ProfileType
import com.example.domain.ReminderProfile
import com.example.domain.ReminderSchedule
import com.example.domain.SessionSummary
import com.example.domain.SoundType
import com.example.domain.StatsRange
import com.example.domain.SupportRequest
import com.example.domain.ThemeMode
import com.example.domain.UserAccount
import com.example.platform.BlinkRuntimeCoordinator
import com.example.platform.BlinkScreenService
import com.example.platform.DevicePrefs
import com.example.platform.ReminderNotificationHelper
import com.example.platform.SoundPlayer
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class EngineStatus {
    IDLE, RUNNING, PAUSED
}

data class ReminderRuntimeState(
    val engineStatus: EngineStatus = EngineStatus.IDLE,
    val nextBlinkSeconds: Int = 25,
    val effectiveBlinkInterval: Int = 25,
    val isEarlyWarningActive: Boolean = false,
    val showBlinkCue: Boolean = false,
    val nextEyeRestSeconds: Int = 1200,
    val isEyeRestActive: Boolean = false,
    val eyeRestRemainingSeconds: Int = 20,
    val sessionElapsedSeconds: Int = 0,
    val sessionBlinksTriggered: Int = 0,
    val sessionEyeRestsCompleted: Int = 0,
    val sessionEyeRestsSkipped: Int = 0,
    val meetingRemainingSeconds: Int = 0,
    val previousProfileIdBeforeMeeting: String? = null,
    val isQuietHoursNow: Boolean = false,
    val activeScheduleName: String? = null,
    val snoozedRemainingSeconds: Int = 0
)

data class BlinkDashboardData(
    val account: UserAccount?,
    val profiles: List<ReminderProfile>,
    val schedules: List<ReminderSchedule>,
    val sessions: List<SessionSummary>,
    val announcements: List<Announcement>,
    val supportRequests: List<SupportRequest>,
    val isAdmin: Boolean
) {
    val activeProfile: ReminderProfile?
        get() = profiles.firstOrNull { it.id == account?.activeProfileId } ?: profiles.firstOrNull()
}

data class AdminPanelData(
    val users: List<UserAccount> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    val auditLogs: List<AdminAuditLog> = emptyList()
)

sealed interface BlinkEvent {
    data class Toast(val message: String) : BlinkEvent
    data class ExportJson(val json: String) : BlinkEvent
    data object AccountDeleted : BlinkEvent
}

class BlinkViewModel(
    private val repository: BlinkRepository,
    val currentUserId: String,
    private val soundPlayer: SoundPlayer,
    private val notificationHelper: ReminderNotificationHelper,
    private val devicePrefs: DevicePrefs? = null
) : ViewModel() {

    private companion object {
        const val TAG = "BlinkVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }

    val isAdmin: Boolean = repository.isAdminUser()

    private val _runtime = MutableStateFlow(ReminderRuntimeState())
    val runtime: StateFlow<ReminderRuntimeState> = _runtime.asStateFlow()

    private val _autoActiveOnScreenOn = MutableStateFlow(devicePrefs?.isAutoActiveOnScreenOn() ?: false)
    val autoActiveOnScreenOn: StateFlow<Boolean> = _autoActiveOnScreenOn.asStateFlow()

    private val _selectedStatsRange = MutableStateFlow(StatsRange.WEEKLY)
    val selectedStatsRange: StateFlow<StatsRange> = _selectedStatsRange.asStateFlow()

    private val _events = MutableSharedFlow<BlinkEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BlinkEvent> = _events.asSharedFlow()

    private var tickerJob: Job? = null
    private var isScreenInteractive: Boolean = true
    private var isUiVisible: Boolean = true

    val uiState: StateFlow<UiState<BlinkDashboardData>> = combine(
        repository.observeUserAccount(currentUserId),
        repository.observeProfiles(currentUserId),
        repository.observeSchedules(currentUserId).onStart { emit(emptyList()) },
        repository.observeSessions(currentUserId).onStart { emit(emptyList()) },
        repository.observePublishedAnnouncements().onStart { emit(emptyList()) },
        repository.observeUserSupportRequests(currentUserId).onStart { emit(emptyList()) }
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        BlinkDashboardData(
            account = values[0] as UserAccount?,
            profiles = (values[1] as List<ReminderProfile>).sortedBy { it.name },
            schedules = (values[2] as List<ReminderSchedule>).sortedBy { it.scheduleName },
            sessions = (values[3] as List<SessionSummary>).sortedByDescending { it.dayKey },
            announcements = values[4] as List<Announcement>,
            supportRequests = values[5] as List<SupportRequest>,
            isAdmin = isAdmin
        )
    }
        .map<BlinkDashboardData, UiState<BlinkDashboardData>> { data ->
            syncEngineWithConfig(data)
            UiState.Success(data)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing Blink25 state", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load Blink25 data"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val adminState: StateFlow<AdminPanelData> = if (isAdmin) {
        combine(
            repository.observeAllUsersForAdmin(),
            repository.observeAllAnnouncementsForAdmin(),
            repository.observeAuditLogs()
        ) { users, announcements, logs ->
            AdminPanelData(
                users = users.sortedBy { it.displayName },
                announcements = announcements,
                auditLogs = logs
            )
        }.catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Admin stream error: ${error.message}")
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AdminPanelData()
        )
    } else {
        flowOf(AdminPanelData()).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = AdminPanelData()
        )
    }

    init {
        viewModelScope.launch {
            repository.ensureUserBootstrapped(currentUserId)
                .onFailure { err ->
                    _events.tryEmit(BlinkEvent.Toast(err.localizedMessage ?: "Sync error"))
                }
        }
        startTickerLoop()
    }

    private fun syncEngineWithConfig(data: BlinkDashboardData) {
        val account = data.account ?: return
        devicePrefs?.saveReminderConfig(account, data.profiles, data.schedules)

        val nowDt = LocalDateTime.now()
        val quietNow = if (account.quietHoursEnabled) {
            val s = BlinkValidators.parseTimeOrNull(account.quietHoursStart)
            val e = BlinkValidators.parseTimeOrNull(account.quietHoursEnd)
            if (s != null && e != null) BlinkValidators.isWithinTimeWindow(nowDt.toLocalTime(), s, e) else false
        } else {
            false
        }
        val activeSched = data.schedules.firstOrNull {
            BlinkValidators.isScheduleActiveAt(it, nowDt.toLocalTime(), nowDt.dayOfWeek)
        }
        val scheduledProfile = activeSched?.let { sched ->
            data.profiles.firstOrNull { it.id == sched.profileId }
        }
        val profile = scheduledProfile ?: data.activeProfile
        val baseBlink = (profile?.blinkIntervalSeconds ?: 25L).toInt().coerceIn(5, 300)
        val effectiveBlink = if (account.focusModeActive) (baseBlink * 2).coerceAtMost(300) else baseBlink

        val shouldAutoStart = _autoActiveOnScreenOn.value &&
            isScreenInteractive &&
            !quietNow &&
            AccountStatus.fromRaw(account.accountStatus) != AccountStatus.SUSPENDED &&
            (data.schedules.none { it.enabled } || activeSched != null)

        _runtime.update { curr ->
            if (curr.engineStatus == EngineStatus.IDLE) {
                val eyeRestSec = ((profile?.eyeRestIntervalMinutes ?: 20L).toInt().coerceIn(5, 180)) * 60
                curr.copy(
                    engineStatus = if (shouldAutoStart) EngineStatus.RUNNING else EngineStatus.IDLE,
                    effectiveBlinkInterval = effectiveBlink,
                    nextBlinkSeconds = effectiveBlink,
                    nextEyeRestSeconds = eyeRestSec,
                    eyeRestRemainingSeconds = (profile?.eyeRestDurationSeconds ?: 20L).toInt().coerceIn(5, 180),
                    isQuietHoursNow = quietNow,
                    activeScheduleName = activeSched?.scheduleName
                )
            } else {
                curr.copy(
                    effectiveBlinkInterval = effectiveBlink,
                    isQuietHoursNow = quietNow,
                    activeScheduleName = activeSched?.scheduleName
                )
            }
        }
    }

    fun setAutoActiveOnScreenOn(enabled: Boolean, context: Context) {
        _autoActiveOnScreenOn.value = enabled
        devicePrefs?.setAutoActiveOnScreenOn(enabled)
        BlinkScreenService.syncWithPreference(
            context = context,
            enabled = enabled,
            isSignedIn = currentUserId.isNotBlank()
        )
        if (enabled && _runtime.value.engineStatus == EngineStatus.IDLE && isScreenInteractive) {
            startSession()
        }
    }

    fun onUiVisibilityChanged(visible: Boolean) {
        isUiVisible = visible
        BlinkRuntimeCoordinator.isUiInForeground = visible
        if (visible) {
            if (isScreenInteractive) startTickerLoop()
        } else {
            // Stop 1-second UI ticker loop in background to conserve battery; BlinkScreenService takes over
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    fun onScreenInteractiveChanged(interactive: Boolean) {
        isScreenInteractive = interactive
        if (!interactive) {
            tickerJob?.cancel()
            tickerJob = null
        } else {
            if (isUiVisible) startTickerLoop()
            if (_autoActiveOnScreenOn.value && _runtime.value.engineStatus == EngineStatus.IDLE) {
                startSession()
            }
        }
    }

    private fun startTickerLoop() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                if (!isUiVisible || !isScreenInteractive) break
                val state = _runtime.value
                // Low battery: skip work unless session is running or standalone eye-rest countdown is active
                if (state.engineStatus == EngineStatus.RUNNING || state.isEyeRestActive || state.meetingRemainingSeconds > 0) {
                    onTickSecond()
                }
            }
        }
    }

    private fun onTickSecond() {
        val currentData = (uiState.value as? UiState.Success)?.data ?: return
        val account = currentData.account ?: return
        if (AccountStatus.fromRaw(account.accountStatus) == AccountStatus.SUSPENDED) {
            if (_runtime.value.engineStatus == EngineStatus.RUNNING) {
                _runtime.update { it.copy(engineStatus = EngineStatus.PAUSED) }
            }
            return
        }

        // Handle Meeting Mode countdown independently even if reminders paused
        if (account.meetingModeActive && _runtime.value.meetingRemainingSeconds > 0) {
            val nextMeeting = _runtime.value.meetingRemainingSeconds - 1
            _runtime.update { it.copy(meetingRemainingSeconds = nextMeeting) }
            if (nextMeeting == 0) {
                cancelMeetingMode()
            }
        }

        val state = _runtime.value
        if (state.engineStatus != EngineStatus.RUNNING) {
            if (state.isEyeRestActive) {
                val profile = currentData.activeProfile
                val eyeRestIntervalSec = ((profile?.eyeRestIntervalMinutes ?: 20L).toInt().coerceIn(5, 180)) * 60
                val eyeRestDurSec = (profile?.eyeRestDurationSeconds ?: 20L).toInt().coerceIn(5, 180)
                val nextRemaining = state.eyeRestRemainingSeconds - 1
                if (nextRemaining <= 0) {
                    _runtime.update {
                        it.copy(
                            isEyeRestActive = false,
                            nextEyeRestSeconds = eyeRestIntervalSec,
                            eyeRestRemainingSeconds = eyeRestDurSec,
                            sessionEyeRestsCompleted = it.sessionEyeRestsCompleted + 1
                        )
                    }
                    notificationHelper.cancelEyeRestCue()
                    if (account.soundEnabled && !state.isQuietHoursNow && !account.focusModeActive && !account.meetingModeActive) {
                        viewModelScope.launch {
                            soundPlayer.play(SoundType.CALM_CHIME, account.soundVolume.toFloat())
                        }
                    }
                } else {
                    _runtime.update { it.copy(eyeRestRemainingSeconds = nextRemaining) }
                }
            }
            return
        }

        // Handle snooze
        if (state.snoozedRemainingSeconds > 0) {
            _runtime.update { it.copy(snoozedRemainingSeconds = it.snoozedRemainingSeconds - 1) }
            return
        }

        val profile = currentData.activeProfile ?: return
        if (!profile.enabled) return

        val nowDt = LocalDateTime.now()
        val quietNow = if (account.quietHoursEnabled) {
            val s = BlinkValidators.parseTimeOrNull(account.quietHoursStart)
            val e = BlinkValidators.parseTimeOrNull(account.quietHoursEnd)
            if (s != null && e != null) BlinkValidators.isWithinTimeWindow(nowDt.toLocalTime(), s, e) else false
        } else {
            false
        }

        val baseBlink = profile.blinkIntervalSeconds.toInt().coerceIn(5, 300)
        val effectiveBlink = if (account.focusModeActive) (baseBlink * 2).coerceAtMost(300) else baseBlink
        val earlySec = if (account.focusModeActive || account.meetingModeActive) 0
        else profile.earlyWarningSeconds.toInt().coerceIn(0, effectiveBlink - 1)
        val eyeRestIntervalSec = (profile.eyeRestIntervalMinutes.toInt().coerceIn(5, 180)) * 60
        val eyeRestDurSec = profile.eyeRestDurationSeconds.toInt().coerceIn(5, 180)

        var newBlinkSec = state.nextBlinkSeconds - 1
        var showBlink = state.showBlinkCue
        var blinksTriggered = state.sessionBlinksTriggered
        var earlyWarning = earlySec > 0 && newBlinkSec in 1..earlySec

        if (newBlinkSec <= 0) {
            newBlinkSec = effectiveBlink
            earlyWarning = false
            blinksTriggered += 1
            if (!quietNow) {
                if (profile.visualReminderEnabled) {
                    showBlink = true
                }
                val canPlaySound = account.soundEnabled &&
                    !account.focusModeActive &&
                    !account.meetingModeActive
                if (canPlaySound) {
                    val sound = SoundType.fromRaw(profile.soundType)
                    val vol = kotlin.math.sqrt((profile.soundVolume * account.soundVolume).coerceIn(0.0, 1.0)).toFloat()
                    viewModelScope.launch { soundPlayer.play(sound, vol) }
                }
                if (account.notificationsEnabled && !account.meetingModeActive) {
                    val isBn = account.languageCode == "bn"
                    notificationHelper.postGentleBlinkCue(
                        "Blink25",
                        if (isBn) "স্বাভাবিকভাবে চোখের পলক ফেলুন" else "Take a soft, natural blink"
                    )
                }
            }
        }

        // Independent Eye-Rest timer
        var newEyeRestSec = state.nextEyeRestSeconds
        var isRestActive = state.isEyeRestActive
        var restRemaining = state.eyeRestRemainingSeconds
        var restsCompleted = state.sessionEyeRestsCompleted

        if (isRestActive) {
            restRemaining -= 1
            if (restRemaining <= 0) {
                isRestActive = false
                restsCompleted += 1
                newEyeRestSec = eyeRestIntervalSec
                restRemaining = eyeRestDurSec
                notificationHelper.cancelEyeRestCue()
                if (account.soundEnabled && !quietNow && !account.focusModeActive && !account.meetingModeActive) {
                    viewModelScope.launch {
                        soundPlayer.play(SoundType.CALM_CHIME, account.soundVolume.toFloat())
                    }
                }
            }
        } else {
            newEyeRestSec -= 1
            if (newEyeRestSec <= 0) {
                isRestActive = true
                restRemaining = eyeRestDurSec
                newEyeRestSec = eyeRestIntervalSec
                if (!quietNow && account.notificationsEnabled && !account.meetingModeActive) {
                    val isBn = account.languageCode == "bn"
                    notificationHelper.postEyeRestCue(
                        if (isBn) "Blink25 • চোখের বিশ্রাম" else "Blink25 • Eye Rest",
                        if (isBn) "প্রায় ২০ ফুট দূরে $eyeRestDurSec সেকেন্ড তাকান।" else "Look ~20 feet away for $eyeRestDurSec seconds.",
                        durationSeconds = eyeRestDurSec
                    )
                }
                if (account.soundEnabled && !quietNow && !account.focusModeActive && !account.meetingModeActive) {
                    viewModelScope.launch {
                        soundPlayer.play(SoundType.GENTLE_BELL, account.soundVolume.toFloat())
                    }
                }
            }
        }

        _runtime.update {
            it.copy(
                effectiveBlinkInterval = effectiveBlink,
                nextBlinkSeconds = newBlinkSec,
                isEarlyWarningActive = earlyWarning,
                showBlinkCue = showBlink,
                nextEyeRestSeconds = newEyeRestSec,
                isEyeRestActive = isRestActive,
                eyeRestRemainingSeconds = restRemaining,
                sessionElapsedSeconds = it.sessionElapsedSeconds + 1,
                sessionBlinksTriggered = blinksTriggered,
                sessionEyeRestsCompleted = restsCompleted,
                isQuietHoursNow = quietNow
            )
        }
    }

    fun startSession() {
        val data = (uiState.value as? UiState.Success)?.data ?: return
        if (AccountStatus.fromRaw(data.account?.accountStatus) == AccountStatus.SUSPENDED) {
            _events.tryEmit(BlinkEvent.Toast("Account is suspended by admin."))
            return
        }
        val profile = data.activeProfile
        val baseBlink = (profile?.blinkIntervalSeconds ?: 25L).toInt().coerceIn(5, 300)
        val effective = if (data.account?.focusModeActive == true) (baseBlink * 2).coerceAtMost(300) else baseBlink
        val eyeRestSec = ((profile?.eyeRestIntervalMinutes ?: 20L).toInt().coerceIn(5, 180)) * 60
        val eyeRestDur = (profile?.eyeRestDurationSeconds ?: 20L).toInt().coerceIn(5, 180)
        _runtime.update {
            it.copy(
                engineStatus = EngineStatus.RUNNING,
                effectiveBlinkInterval = effective,
                nextBlinkSeconds = effective,
                nextEyeRestSeconds = eyeRestSec,
                eyeRestRemainingSeconds = eyeRestDur,
                showBlinkCue = false,
                isEarlyWarningActive = false,
                isEyeRestActive = false,
                snoozedRemainingSeconds = 0
            )
        }
    }

    fun pauseSession() {
        _runtime.update { it.copy(engineStatus = EngineStatus.PAUSED, showBlinkCue = false) }
    }

    fun resumeSession() {
        _runtime.update { it.copy(engineStatus = EngineStatus.RUNNING) }
    }

    fun stopSession() {
        val snapshot = _runtime.value
        val data = (uiState.value as? UiState.Success)?.data
        val profile = data?.activeProfile
        _runtime.update {
            val baseBlink = (profile?.blinkIntervalSeconds ?: 25L).toInt().coerceIn(5, 300)
            val eyeRestSec = ((profile?.eyeRestIntervalMinutes ?: 20L).toInt().coerceIn(5, 180)) * 60
            val eyeRestDur = (profile?.eyeRestDurationSeconds ?: 20L).toInt().coerceIn(5, 180)
            ReminderRuntimeState(
                engineStatus = EngineStatus.IDLE,
                nextBlinkSeconds = baseBlink,
                effectiveBlinkInterval = baseBlink,
                nextEyeRestSeconds = eyeRestSec,
                eyeRestRemainingSeconds = eyeRestDur,
                isQuietHoursNow = snapshot.isQuietHoursNow,
                activeScheduleName = snapshot.activeScheduleName
            )
        }
        notificationHelper.cancelAll()

        if (snapshot.sessionElapsedSeconds >= 5 && profile != null) {
            val summary = SessionSummary(
                userId = currentUserId,
                profileId = profile.id.ifBlank { "work_default" },
                profileName = profile.name,
                durationSeconds = snapshot.sessionElapsedSeconds.toLong(),
                blinksTriggered = snapshot.sessionBlinksTriggered.toLong(),
                eyeRestsCompleted = snapshot.sessionEyeRestsCompleted.toLong(),
                eyeRestsSkipped = snapshot.sessionEyeRestsSkipped.toLong(),
                dayKey = LocalDate.now().toString()
            )
            viewModelScope.launch {
                repository.recordSession(currentUserId, summary)
                    .onFailure { _events.tryEmit(BlinkEvent.Toast(it.localizedMessage ?: "Failed to save session")) }
            }
        }
    }

    fun dismissBlinkCue() {
        _runtime.update { it.copy(showBlinkCue = false) }
    }

    fun snoozeAll(minutes: Int = 5) {
        notificationHelper.cancelAll()
        _runtime.update {
            it.copy(
                snoozedRemainingSeconds = minutes.coerceIn(1, 60) * 60,
                showBlinkCue = false,
                isEyeRestActive = false
            )
        }
        _events.tryEmit(BlinkEvent.Toast("Snoozed for $minutes min"))
    }

    fun startEyeRestNow() {
        val data = (uiState.value as? UiState.Success)?.data
        val dur = (data?.activeProfile?.eyeRestDurationSeconds ?: 20L).toInt().coerceIn(5, 180)
        _runtime.update {
            it.copy(
                isEyeRestActive = true,
                eyeRestRemainingSeconds = dur,
                showBlinkCue = false
            )
        }
    }

    fun dismissEyeRest() {
        notificationHelper.cancelEyeRestCue()
        val data = (uiState.value as? UiState.Success)?.data
        val intervalSec = ((data?.activeProfile?.eyeRestIntervalMinutes ?: 20L).toInt().coerceIn(5, 180)) * 60
        val dur = (data?.activeProfile?.eyeRestDurationSeconds ?: 20L).toInt().coerceIn(5, 180)
        _runtime.update {
            it.copy(
                isEyeRestActive = false,
                nextEyeRestSeconds = intervalSec,
                eyeRestRemainingSeconds = dur
            )
        }
    }

    fun snoozeEyeRest() {
        notificationHelper.cancelEyeRestCue()
        val data = (uiState.value as? UiState.Success)?.data
        val snoozeMin = (data?.activeProfile?.snoozeMinutes ?: 5L).toInt().coerceIn(1, 60)
        _runtime.update {
            it.copy(
                isEyeRestActive = false,
                nextEyeRestSeconds = snoozeMin * 60
            )
        }
        _events.tryEmit(BlinkEvent.Toast("Eye rest snoozed for $snoozeMin min"))
    }

    fun skipEyeRest() {
        notificationHelper.cancelEyeRestCue()
        val data = (uiState.value as? UiState.Success)?.data
        val intervalSec = ((data?.activeProfile?.eyeRestIntervalMinutes ?: 20L).toInt().coerceIn(5, 180)) * 60
        _runtime.update {
            it.copy(
                isEyeRestActive = false,
                nextEyeRestSeconds = intervalSec,
                sessionEyeRestsSkipped = it.sessionEyeRestsSkipped + 1
            )
        }
    }

    fun activateProfile(profileId: String) {
        viewModelScope.launch {
            repository.updateUserSettings(currentUserId, mapOf("activeProfileId" to profileId))
                .onSuccess {
                    val data = (uiState.value as? UiState.Success)?.data
                    val prof = data?.profiles?.firstOrNull { it.id == profileId }
                    if (prof != null) {
                        val baseBlink = prof.blinkIntervalSeconds.toInt().coerceIn(5, 300)
                        _runtime.update {
                            it.copy(
                                nextBlinkSeconds = baseBlink,
                                effectiveBlinkInterval = baseBlink,
                                nextEyeRestSeconds = prof.eyeRestIntervalMinutes.toInt() * 60,
                                eyeRestRemainingSeconds = prof.eyeRestDurationSeconds.toInt()
                            )
                        }
                    }
                }
        }
    }

    fun toggleSound() {
        val account = (uiState.value as? UiState.Success)?.data?.account ?: return
        viewModelScope.launch {
            repository.updateUserSettings(currentUserId, mapOf("soundEnabled" to !account.soundEnabled))
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateUserSettings(currentUserId, mapOf("notificationsEnabled" to enabled))
        }
    }

    fun toggleFocusMode() {
        val account = (uiState.value as? UiState.Success)?.data?.account ?: return
        viewModelScope.launch {
            repository.updateUserSettings(currentUserId, mapOf("focusModeActive" to !account.focusModeActive))
        }
    }

    fun activateMeetingMode(minutes: Int) {
        val data = (uiState.value as? UiState.Success)?.data ?: return
        val account = data.account ?: return
        val prevProfileId = account.activeProfileId
        val meetingProfile = data.profiles.firstOrNull { it.profileType == ProfileType.MEETING.name }
        _runtime.update {
            it.copy(
                previousProfileIdBeforeMeeting = prevProfileId,
                meetingRemainingSeconds = minutes.coerceIn(5, 240) * 60
            )
        }
        viewModelScope.launch {
            val updates = mutableMapOf<String, Any?>(
                "meetingModeActive" to true,
                "meetingModeMinutes" to minutes.toLong()
            )
            if (meetingProfile != null) {
                updates["activeProfileId"] = meetingProfile.id
            }
            repository.updateUserSettings(currentUserId, updates)
        }
    }

    fun cancelMeetingMode() {
        val prevProfileId = _runtime.value.previousProfileIdBeforeMeeting
        _runtime.update { it.copy(meetingRemainingSeconds = 0, previousProfileIdBeforeMeeting = null) }
        viewModelScope.launch {
            val updates = mutableMapOf<String, Any?>("meetingModeActive" to false)
            if (!prevProfileId.isNullOrBlank()) {
                updates["activeProfileId"] = prevProfileId
            }
            repository.updateUserSettings(currentUserId, updates)
        }
    }

    fun updateSettings(updates: Map<String, Any?>) {
        viewModelScope.launch {
            repository.updateUserSettings(currentUserId, updates)
                .onFailure { _events.tryEmit(BlinkEvent.Toast(it.localizedMessage ?: "Failed to update settings")) }
        }
    }

    fun previewSound(soundType: SoundType, volume: Float) {
        viewModelScope.launch { soundPlayer.play(soundType, volume) }
    }

    fun setStatsRange(range: StatsRange) {
        _selectedStatsRange.value = range
    }

    fun saveProfile(profile: ReminderProfile, isNew: Boolean, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.saveProfile(currentUserId, profile, isNew)
            result.fold(
                onSuccess = { onDone(true, null) },
                onFailure = { onDone(false, it.localizedMessage ?: "Could not save profile") }
            )
        }
    }

    fun duplicateProfile(profile: ReminderProfile) {
        val copy = profile.copy(
            id = "",
            name = "${profile.name.take(42)} Copy",
            profileType = ProfileType.CUSTOM.name
        )
        viewModelScope.launch {
            repository.saveProfile(currentUserId, copy, isNew = true)
                .onSuccess { _events.tryEmit(BlinkEvent.Toast("Profile duplicated")) }
                .onFailure { _events.tryEmit(BlinkEvent.Toast(it.localizedMessage ?: "Duplicate failed")) }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteProfile(currentUserId, profileId)
                .onFailure { _events.tryEmit(BlinkEvent.Toast(it.localizedMessage ?: "Delete failed")) }
        }
    }

    fun saveSchedule(schedule: ReminderSchedule, isNew: Boolean, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.saveSchedule(currentUserId, schedule, isNew)
            result.fold(
                onSuccess = { onDone(true, null) },
                onFailure = { onDone(false, it.localizedMessage ?: "Could not save schedule") }
            )
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            repository.deleteSchedule(currentUserId, scheduleId)
                .onFailure { _events.tryEmit(BlinkEvent.Toast(it.localizedMessage ?: "Delete failed")) }
        }
    }

    fun submitSupportRequest(subject: String, message: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repository.submitSupportRequest(currentUserId, subject, message).fold(
                onSuccess = { onDone(true, null) },
                onFailure = { onDone(false, it.localizedMessage ?: "Could not submit request") }
            )
        }
    }

    fun exportUserData() {
        viewModelScope.launch {
            repository.exportUserDataJson(currentUserId).fold(
                onSuccess = { json -> _events.tryEmit(BlinkEvent.ExportJson(json)) },
                onFailure = { _events.tryEmit(BlinkEvent.Toast(it.localizedMessage ?: "Export failed")) }
            )
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteAllUserData(currentUserId).fold(
                onSuccess = {
                    _events.tryEmit(BlinkEvent.AccountDeleted)
                    onComplete()
                },
                onFailure = { err ->
                    _events.tryEmit(BlinkEvent.Toast(err.localizedMessage ?: "Re-sign in may be required to delete account"))
                }
            )
        }
    }

    // --- ADMIN ACTIONS ---
    fun setUserAccountStatus(targetUserId: String, status: AccountStatus) {
        viewModelScope.launch {
            repository.setUserAccountStatus(targetUserId, status)
                .onSuccess { _events.tryEmit(BlinkEvent.Toast("Updated user status to ${status.name}")) }
                .onFailure { _events.tryEmit(BlinkEvent.Toast(it.localizedMessage ?: "Admin action failed")) }
        }
    }

    fun createAnnouncement(title: String, message: String, published: Boolean) {
        viewModelScope.launch {
            repository.createAnnouncement(title, message, published)
                .onSuccess { _events.tryEmit(BlinkEvent.Toast("Announcement saved")) }
                .onFailure { _events.tryEmit(BlinkEvent.Toast(it.localizedMessage ?: "Failed to create announcement")) }
        }
    }

    fun toggleAnnouncement(id: String, published: Boolean) {
        viewModelScope.launch {
            repository.toggleAnnouncementPublished(id, published)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}
