package com.walhalla.shiftalarm.feature.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walhalla.shiftalarm.core.alarm.RingingController
import com.walhalla.shiftalarm.core.model.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RingingViewModel @Inject constructor(
    private val ringingController: RingingController,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val shown = MutableStateFlow(Shown())

    val uiState: StateFlow<RingingUiState> = combine(
        shown,
        settingsRepository.settings,
    ) { current, settings ->
        RingingUiState(
            shiftName = current.shiftName,
            triggerAtEpochMillis = current.triggerAtEpochMillis,
            snoozeMinutes = settings.snoozeMinutes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RingingUiState(),
    )

    fun show(shiftName: String, triggerAtEpochMillis: Long) {
        shown.value = Shown(shiftName, triggerAtEpochMillis)
    }

    fun stop() {
        ringingController.stop()
    }

    fun snooze() {
        ringingController.snooze()
    }

    private data class Shown(
        val shiftName: String = "",
        val triggerAtEpochMillis: Long = 0L,
    )
}
