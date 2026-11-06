package com.example.aiknowledgefeed.frontend.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.aiknowledgefeed.R
import com.example.aiknowledgefeed.frontend.screens.DetailScreen
import com.example.aiknowledgefeed.frontend.screens.FeedScreen
import com.example.aiknowledgefeed.frontend.screens.KnowledgeScreen
import com.example.aiknowledgefeed.frontend.screens.SettingsScreen

private enum class Destination(val label: Int, val symbol: String) {
    Feed(R.string.screen_feed, "F"),
    Detail(R.string.screen_detail, "D"),
    Knowledge(R.string.screen_knowledge, "W"),
    Settings(R.string.screen_settings, "E"),
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = backStackEntry?.destination?.route == destination.name,
                        onClick = {
                            navController.navigate(destination.name) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(destination.symbol) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Feed.name,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Feed.name) { FeedScreen() }
            composable(Destination.Detail.name) { DetailScreen() }
            composable(Destination.Knowledge.name) { KnowledgeScreen() }
            composable(Destination.Settings.name) { SettingsScreen() }
        }
    }
}
