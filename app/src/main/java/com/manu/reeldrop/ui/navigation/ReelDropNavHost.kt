package com.manu.reeldrop.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.manu.reeldrop.ui.screens.AboutScreen
import com.manu.reeldrop.ui.screens.HomeScreen
import com.manu.reeldrop.ui.screens.LibraryScreen
import com.manu.reeldrop.ui.screens.QueueScreen
import com.manu.reeldrop.ui.screens.SettingsScreen

enum class ReelTab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("home", "Descargar", Icons.Outlined.Download, Icons.Filled.Download),
    QUEUE("queue", "Descargas", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    LIBRARY("library", "Biblioteca", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    SETTINGS("settings", "Ajustes", Icons.Outlined.Settings, Icons.Filled.Settings),
    ABOUT("about", "Acerca de", Icons.Outlined.Info, Icons.Filled.Info),
}

private val bottomTabs = listOf(ReelTab.HOME, ReelTab.QUEUE, ReelTab.LIBRARY, ReelTab.SETTINGS)

@Composable
fun ReelDropRoot(
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
    openQueue: Boolean,
    onQueueOpened: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var pendingLibraryItem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openQueue) {
        if (openQueue) {
            navController.navigate(ReelTab.QUEUE.route) { launchSingleTop = true }
            onQueueOpened()
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(visible = currentRoute != ReelTab.ABOUT.route) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(navController = navController, startDestination = ReelTab.HOME.route) {
                composable(ReelTab.HOME.route) {
                    HomeScreen(
                        sharedUrl = sharedUrl,
                        onSharedUrlConsumed = onSharedUrlConsumed,
                        onOpenQueue = { navController.navigate(ReelTab.QUEUE.route) { launchSingleTop = true } },
                        onOpenSettings = { navController.navigate(ReelTab.SETTINGS.route) { launchSingleTop = true } },
                    )
                }
                composable(ReelTab.QUEUE.route) {
                    QueueScreen(
                        onOpenLibrary = { navController.navigate(ReelTab.LIBRARY.route) { launchSingleTop = true } },
                    )
                }
                composable(ReelTab.LIBRARY.route) {
                    LibraryScreen(
                        initialSelectedFile = pendingLibraryItem,
                        onPlayerClosed = { pendingLibraryItem = null },
                    )
                }
                composable(ReelTab.SETTINGS.route) {
                    SettingsScreen(
                        onOpenAbout = { navController.navigate(ReelTab.ABOUT.route) { launchSingleTop = true } },
                    )
                }
                composable(ReelTab.ABOUT.route) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
