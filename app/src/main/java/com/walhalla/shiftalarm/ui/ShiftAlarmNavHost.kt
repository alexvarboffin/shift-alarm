package com.walhalla.shiftalarm.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.walhalla.shiftalarm.core.ui.ShiftRoutes
import com.walhalla.shiftalarm.feature.calendar.CalendarRoute
import com.walhalla.shiftalarm.feature.schedule.ScheduleRoute

private data class Tab(
    val route: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftAlarmNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: ShiftRoutes.SCHEDULE
    val tabs = listOf(
        Tab(ShiftRoutes.SCHEDULE, "График"),
        Tab(ShiftRoutes.CALENDAR, "Календарь"),
        Tab(ShiftRoutes.SETTINGS, "Настройки"),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(tabs.firstOrNull { it.route == route }?.label ?: "График")
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab.route) {
                                    ShiftRoutes.CALENDAR -> Icons.Filled.DateRange
                                    ShiftRoutes.SETTINGS -> Icons.Filled.Settings
                                    else -> Icons.Filled.Notifications
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ShiftRoutes.SCHEDULE,
            modifier = Modifier.padding(padding),
        ) {
            composable(ShiftRoutes.SCHEDULE) { ScheduleRoute() }
            composable(ShiftRoutes.CALENDAR) { CalendarRoute() }
            composable(ShiftRoutes.SETTINGS) { SettingsRoute() }
        }
    }
}
