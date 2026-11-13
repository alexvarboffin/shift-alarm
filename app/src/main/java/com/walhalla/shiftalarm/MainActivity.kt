package com.walhalla.shiftalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walhalla.shiftalarm.core.ui.ShiftAlarmTheme
import com.walhalla.shiftalarm.ui.SettingsViewModel
import com.walhalla.shiftalarm.ui.ShiftAlarmNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

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
}
