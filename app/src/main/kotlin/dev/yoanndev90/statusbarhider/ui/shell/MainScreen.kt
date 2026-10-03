package dev.yoanndev90.statusbarhider.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.yoanndev90.statusbarhider.core.command.CommandExecutor
import dev.yoanndev90.statusbarhider.features.bar.BarScreen
import dev.yoanndev90.statusbarhider.features.bar.BarViewModel
import dev.yoanndev90.statusbarhider.features.logs.LogsScreen
import dev.yoanndev90.statusbarhider.features.status.StatusScreen
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.features.style.StyleScreen
import dev.yoanndev90.statusbarhider.features.style.StyleViewModel

/**
 * Shell of the app: a top bar titled after the current tab, a bottom
 * navigation bar over four destinations, and the NavHost wiring them to one
 * ViewModel per screen (each scoped to its own back stack entry).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
	val navController = rememberNavController()
	val backStackEntry by navController.currentBackStackEntryAsState()
	val current = AppDestination.fromRoute(backStackEntry?.destination?.route)
	val busy by CommandExecutor.busy.collectAsStateWithLifecycle()

	Scaffold(
		modifier = Modifier.fillMaxSize(),
		topBar = {
			Column {
				TopAppBar(title = { Text(stringResource(current.titleRes)) })
				if (busy) {
					LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
				}
			}
		},
		bottomBar = {
			NavigationBar {
				AppDestination.entries.forEach { destination ->
					NavigationBarItem(
						selected = destination == current,
						onClick = { navController.navigateTo(destination) },
						icon = { Icon(painter = painterResource(destination.iconRes), contentDescription = null) },
						label = { Text(stringResource(destination.labelRes)) }
					)
				}
			}
		}
	) { inner ->
		NavHost(
			navController = navController,
			startDestination = AppDestination.START.route,
			modifier = Modifier.padding(inner)
		) {
			composable(AppDestination.STATUS.route) { StatusScreen(viewModel<StatusViewModel>()) }
			composable(AppDestination.BAR.route) { BarScreen(viewModel<BarViewModel>()) }
			composable(AppDestination.STYLE.route) { StyleScreen(viewModel<StyleViewModel>()) }
			composable(AppDestination.LOG.route) { LogsScreen() }
		}
	}
}

/**
 * Tab switch keeping one entry per tab: back returns to [AppDestination.START]
 * instead of replaying the visit order, while each tab's scroll position and
 * local state are saved and restored.
 */
private fun NavHostController.navigateTo(destination: AppDestination) {
	navigate(destination.route) {
		popUpTo(AppDestination.START.route) { saveState = true }
		launchSingleTop = true
		restoreState = true
	}
}
