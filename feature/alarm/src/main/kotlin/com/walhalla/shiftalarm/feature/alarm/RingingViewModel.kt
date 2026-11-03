package com.walhalla.shiftalarm.feature.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walhalla.shiftalarm.core.alarm.RingingController
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RingingViewModel @Inject constructor(
    private val ringingController: RingingController,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val shown = MutableStateFlow<Shown?>(null)

    val useDynamicColor: StateFlow<Boolean> = settingsRepository.settings
        .map { it.useDynamicColor }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    val uiState: StateFlow<RingingUiState> = combine(
        shown,
        settingsRepository.settings,
    ) { current, settings ->
        when {
            current == null -> RingingUiState.Loading
            current.shiftName.isBlank() -> RingingUiState.Error(UiText.Raw("Нет данных будильника"))
            else -> RingingUiState.Ringing(
                shiftName = current.shiftName,
                triggerAtEpochMillis = current.triggerAtEpochMillis,
                snoozeMinutes = settings.snoozeMinutes,
            )
        }
    }.catch { error ->
        emit(RingingUiState.Error(UiText.Raw(error.message ?: "Не удалось открыть будильник")))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RingingUiState.Loading,
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
        val shiftName: String,
        val triggerAtEpochMillis: Long,
    )
}
