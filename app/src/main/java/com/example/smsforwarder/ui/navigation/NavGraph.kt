package com.example.smsforwarder.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.smsforwarder.BuildConfig
import com.example.smsforwarder.ui.MainViewModel
import com.example.smsforwarder.ui.screen.BackupRestoreScreen
import com.example.smsforwarder.ui.screen.ExecutionModeScreen
import com.example.smsforwarder.ui.screen.FilterEditScreen
import com.example.smsforwarder.ui.screen.FilterListScreen
import com.example.smsforwarder.ui.screen.LogListScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object FilterList : Screen("filter_list")
    object FilterEdit : Screen("filter_edit?filterId={filterId}") {
        fun createRoute(filterId: Long = 0L) = "filter_edit?filterId=$filterId"
    }
    object LogList : Screen("log_list")
    object ExecutionMode : Screen("execution_mode")
    object BackupRestore : Screen("backup_restore")
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val configuration = LocalConfiguration.current
    val drawerWidth = (configuration.screenWidthDp * 0.825f).dp

    // System Back button closes Drawer first if open
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    val navigateFromDrawer: (String) -> Unit = { targetRoute ->
        coroutineScope.launch { drawerState.close() }
        if (currentRoute != targetRoute) {
            if (targetRoute == Screen.FilterList.route) {
                navController.navigate(Screen.FilterList.route) {
                    popUpTo(Screen.FilterList.route) { inclusive = false }
                    launchSingleTop = true
                }
            } else {
                navController.navigate(targetRoute) {
                    popUpTo(Screen.FilterList.route) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val safePopBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            navController.navigate(Screen.FilterList.route) {
                popUpTo(Screen.FilterList.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute == Screen.FilterList.route,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "SMSForwarder 메뉴",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                    label = { Text("포워딩 필터 목록") },
                    selected = currentRoute == Screen.FilterList.route,
                    onClick = { navigateFromDrawer(Screen.FilterList.route) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("백그라운드/포그라운드 설정") },
                    selected = currentRoute == Screen.ExecutionMode.route,
                    onClick = { navigateFromDrawer(Screen.ExecutionMode.route) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("포워딩 로그 확인") },
                    selected = currentRoute == Screen.LogList.route,
                    onClick = { navigateFromDrawer(Screen.LogList.route) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    label = { Text("백업 및 복구 기능") },
                    selected = currentRoute == Screen.BackupRestore.route,
                    onClick = { navigateFromDrawer(Screen.BackupRestore.route) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                HorizontalDivider()

                Text(
                    text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.FilterList.route
        ) {
            composable(Screen.FilterList.route) {
                FilterListScreen(
                    viewModel = viewModel,
                    onOpenDrawer = {
                        coroutineScope.launch { drawerState.open() }
                    },
                    onAddFilter = {
                        navController.navigate(Screen.FilterEdit.createRoute(0L))
                    },
                    onEditFilter = { filterId ->
                        navController.navigate(Screen.FilterEdit.createRoute(filterId))
                    },
                    onNavigateToLogs = {
                        navigateFromDrawer(Screen.LogList.route)
                    },
                    onNavigateToSettings = {
                        navigateFromDrawer(Screen.ExecutionMode.route)
                    }
                )
            }

            composable(
                route = Screen.FilterEdit.route,
                arguments = listOf(
                    navArgument("filterId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { backStackEntry ->
                val filterId = backStackEntry.arguments?.getLong("filterId") ?: 0L
                FilterEditScreen(
                    filterId = filterId,
                    viewModel = viewModel,
                    onNavigateBack = safePopBack
                )
            }

            composable(Screen.LogList.route) {
                LogListScreen(
                    viewModel = viewModel,
                    onNavigateBack = safePopBack
                )
            }

            composable(Screen.ExecutionMode.route) {
                ExecutionModeScreen(
                    viewModel = viewModel,
                    onNavigateBack = safePopBack
                )
            }

            composable(Screen.BackupRestore.route) {
                BackupRestoreScreen(
                    viewModel = viewModel,
                    onNavigateBack = safePopBack
                )
            }
        }
    }
}
