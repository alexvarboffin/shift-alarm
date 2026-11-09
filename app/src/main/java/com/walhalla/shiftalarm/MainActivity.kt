package com.walhalla.shiftalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walhalla.shiftalarm.core.alarm.RingingController
import com.walhalla.shiftalarm.core.alarm.RingingLaunchIntent
import com.walhalla.shiftalarm.core.ui.ShiftAlarmTheme
import com.walhalla.shiftalarm.ui.SettingsViewModel
import com.walhalla.shiftalarm.ui.ShiftAlarmNavHost
import java.time.Clock
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModel()
    private val ringingController: RingingController by inject()
    private val ringingLaunchIntent: RingingLaunchIntent by inject()
    private val clock: Clock by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
            ShiftAlarmTheme(useDynamicColor = settings.useDynamicColor) {
                ShiftAlarmNavHost()
            }
        }
    }

    /**
     * A ring is going on but its screen did not pop up (channel off, no full-screen intent):
     * opening the app shows Stop and Snooze. The service never starts an activity from the background.
     */
    override fun onResume() {
        super.onResume()
        if (ringingController.isRinging.value) {
            startActivity(ringingLaunchIntent.create(DEFAULT_SHIFT_NAME, clock.millis(), snooze = false))
        }
    }

    private companion object {
        const val DEFAULT_SHIFT_NAME = "Смена"
    }
}
