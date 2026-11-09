package com.walhalla.shiftalarm.feature.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walhalla.shiftalarm.core.alarm.AlarmIntents
import com.walhalla.shiftalarm.core.alarm.RingingService
import com.walhalla.shiftalarm.core.ui.ShiftAlarmTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class RingingActivity : ComponentActivity() {
    private val viewModel: RingingViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLockScreenFlags()
        enableEdgeToEdge()
        publish(intent)
        if (savedInstanceState == null) startRingingIfFallback(intent)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
            ShiftAlarmTheme(useDynamicColor = dynamicColor) {
                RingingScreen(
                    state = state,
                    onStop = {
                        viewModel.stop()
                        finish()
                    },
                    onSnooze = {
                        viewModel.snooze()
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publish(intent)
        startRingingIfFallback(intent)
    }

    /** Fallback path: the activity is visible now, so it may start the ringing FGS itself. */
    private fun startRingingIfFallback(source: Intent?) {
        if (source?.getBooleanExtra(AlarmIntents.EXTRA_FALLBACK, false) != true) return
        val ringing = Intent(this, RingingService::class.java)
            .setAction(RingingService.ACTION_FIRE)
            .putExtras(source)
        runCatching { ContextCompat.startForegroundService(this, ringing) }
        // Once handled, a recreate or a later resume must not start it again.
        source.removeExtra(AlarmIntents.EXTRA_FALLBACK)
    }

    private fun publish(source: Intent?) {
        viewModel.show(
            shiftName = source?.getStringExtra(AlarmIntents.EXTRA_SHIFT_NAME).orEmpty(),
            triggerAtEpochMillis = source?.getLongExtra(AlarmIntents.EXTRA_TRIGGER_AT_MILLIS, 0L) ?: 0L,
        )
    }

    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
