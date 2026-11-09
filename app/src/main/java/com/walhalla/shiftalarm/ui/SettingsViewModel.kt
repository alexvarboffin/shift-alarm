package com.walhalla.shiftalarm.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walhalla.shiftalarm.R
import com.walhalla.shiftalarm.core.alarm.AlarmScheduler
import com.walhalla.shiftalarm.core.model.AppPermission
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.model.UserSettings
import com.walhalla.shiftalarm.core.model.keepAlive
import com.walhalla.shiftalarm.core.ui.UiText
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel internal constructor(
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    private val context: Context,
    private val clock: Clock,
    scopeOverride: CoroutineScope?,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : ViewModel() {
    private val workScope = scopeOverride ?: viewModelScope
    private val grants = MutableStateFlow(readGrants())
    private val notificationRationale = MutableStateFlow(false)
    private val readError = MutableStateFlow<UiText?>(null)
    private val reload = MutableStateFlow(0)

    constructor(
        settingsRepository: SettingsRepository,
        alarmScheduler: AlarmScheduler,
        context: Context,
        clock: Clock,
    ) : this(settingsRepository, alarmScheduler, context, clock, null)

    private val settings = reload.flatMapLatest {
        settingsRepository.settings.keepAlive(UserSettings()) { readError.value = readFailure() }
    }

    private val requested = reload.flatMapLatest {
        settingsRepository.requestedPermissions.keepAlive(emptySet()) { readError.value = readFailure() }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settings,
        requested,
        grants,
        notificationRationale,
        readError,
    ) { prefs, asked, grant, rationale, error ->
        val base = SettingsUiState(
            isLoading = false,
            selectedSnooze = prefs.snoozeMinutes,
            vibrationEnabled = prefs.vibrationEnabled,
            useDynamicColor = prefs.useDynamicColor,
            errorMessage = error,
        )
        if (error != null) {
            base
        } else {
            base.copy(
                notificationStatus = notificationStatus(
                    sdkInt = sdkInt,
                    enabled = grant.notificationsEnabled,
                    granted = grant.notifications,
                    requested = AppPermission.NOTIFICATIONS in asked,
                    rationale = rationale,
                ),
                exactAlarmStatus = specialAccessStatus(
                    sdkInt = sdkInt,
                    since = Build.VERSION_CODES.S,
                    granted = grant.exact,
                    requested = AppPermission.EXACT_ALARM in asked,
                ),
                fullScreenIntentStatus = specialAccessStatus(
                    sdkInt = sdkInt,
                    since = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                    granted = grant.fullScreen,
                    requested = AppPermission.FULL_SCREEN_INTENT in asked,
                ),
            )
        }
    }.stateIn(
        scope = workScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    private fun readFailure(): UiText = UiText.Res(R.string.settings_error_read)

    /**
     * Re-reads grants, for example on resume. Pass [notificationRationale] when the
     * Activity knows it; null keeps the last value.
     */
    fun refreshPermissions(notificationRationale: Boolean? = null) {
        if (notificationRationale != null) this.notificationRationale.value = notificationRationale
        grants.update { readGrants() }
        workScope.launch { alarmScheduler.scheduleNext(clock.millis()) }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectSnooze -> {
                if (event.minutes !in SettingsUiState.SNOOZE_OPTIONS) return
                workScope.launch { settingsRepository.setSnoozeMinutes(event.minutes) }
            }
            is SettingsEvent.SetUseDynamicColor -> {
                workScope.launch { settingsRepository.setUseDynamicColor(event.enabled) }
            }
            is SettingsEvent.PermissionRequested -> {
                workScope.launch {
                    try {
                        settingsRepository.markRequested(event.permission)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        readError.value = UiText.Res(R.string.settings_error_write)
                    }
                }
            }
            SettingsEvent.Retry -> {
                readError.value = null
                reload.update { it + 1 }
                refreshPermissions()
            }
        }
    }

    fun setSnoozeMinutes(minutes: Int) {
        onEvent(SettingsEvent.SelectSnooze(minutes))
    }

    fun setVibrationEnabled(enabled: Boolean) {
        workScope.launch { settingsRepository.setVibrationEnabled(enabled) }
    }

    private fun readGrants(): Grants = Grants(
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        notifications = notificationsGranted(),
        exact = alarmScheduler.canScheduleExactAlarms(),
        fullScreen = alarmScheduler.canUseFullScreenIntent(),
    )

    /** Runtime permission only from API 33; below it [notificationStatus] uses areNotificationsEnabled. */
    private fun notificationsGranted(): Boolean {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class Grants(
        val notificationsEnabled: Boolean,
        val notifications: Boolean,
        val exact: Boolean,
        val fullScreen: Boolean,
    )
}
