package com.walhalla.shiftalarm.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.walhalla.shiftalarm.core.model.HomeWidgetRefresher
import com.walhalla.shiftalarm.core.model.ShiftRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

class TomorrowShiftWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val repository = entryPoint.shiftRepository()
        val pattern = repository.observeActivePattern().first()
        val overrides = repository.observeOverrides().first().associateBy { it.date }
        val label = tomorrowLabel(pattern, overrides)
        val openApp = actionStartActivity(openAppIntent(context))
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(day = Color(0xFFF4F1EA), night = Color(0xFF12161C)))
                    .padding(16.dp)
                    .clickable(openApp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF1B3A4B), night = Color(0xFFF4F1EA)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

class TomorrowShiftWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TomorrowShiftWidget()
}

/** Explicit component. Glance turns this into one immutable click PendingIntent. */
internal fun openAppIntent(context: Context): Intent =
    Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(context.packageName, MAIN_ACTIVITY)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

private const val MAIN_ACTIVITY: String = "com.walhalla.shiftalarm.MainActivity"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun shiftRepository(): ShiftRepository
}

@Singleton
class GlanceHomeWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : HomeWidgetRefresher {
    override suspend fun refresh() {
        TomorrowShiftWidget().updateAll(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetBindings {
    @Binds
    abstract fun bindRefresher(impl: GlanceHomeWidgetRefresher): HomeWidgetRefresher
}
