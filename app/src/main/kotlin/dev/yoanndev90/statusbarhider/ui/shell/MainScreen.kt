package dev.yoanndev90.statusbarhider.ui.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.core.settings.sections.BatterySection
import dev.yoanndev90.statusbarhider.core.settings.sections.ConnectivitySection
import dev.yoanndev90.statusbarhider.core.settings.sections.GeneralSection
import dev.yoanndev90.statusbarhider.data.AppSettings
import dev.yoanndev90.statusbarhider.features.home.HomeScreen
import dev.yoanndev90.statusbarhider.features.logs.LogsScreen
import dev.yoanndev90.statusbarhider.features.logs.LogsViewModel
import dev.yoanndev90.statusbarhider.features.settings.AboutScreen
import dev.yoanndev90.statusbarhider.features.settings.AppearanceScreen
import dev.yoanndev90.statusbarhider.features.settings.BackupScreen
import dev.yoanndev90.statusbarhider.features.settings.ClockScreen
import dev.yoanndev90.statusbarhider.features.settings.CutoutScreen
import dev.yoanndev90.statusbarhider.features.settings.DebugHubScreen
import dev.yoanndev90.statusbarhider.features.settings.DebugInfoScreen
import dev.yoanndev90.statusbarhider.features.settings.DebugPermissionsScreen
import dev.yoanndev90.statusbarhider.features.settings.HiddenAppsScreen
import dev.yoanndev90.statusbarhider.features.settings.LockAppearanceScreen
import dev.yoanndev90.statusbarhider.features.settings.LockBehaviorScreen
import dev.yoanndev90.statusbarhider.features.settings.LockHubScreen
import dev.yoanndev90.statusbarhider.features.settings.NotificationsScreen
import dev.yoanndev90.statusbarhider.features.settings.OemScreen
import dev.yoanndev90.statusbarhider.features.settings.ProfilesScreen
import dev.yoanndev90.statusbarhider.features.settings.SettingsRootScreen
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingSectionScreen
import dev.yoanndev90.statusbarhider.ui.setup.SetupScreen

/**
 * App shell: a NavHost over Home and the Settings tree, a top bar titled after
 * the current route with a back arrow on every non-root screen, and the setup
 * checklist drawn on top when it is open.
 *
 * There is no bottom navigation: Home is the only entry point, the gear in its
 * top bar opens Settings, and every category is a stack destination. The top
 * bar carries the contextual actions: search on Settings, copy / share on Logs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
	val context = LocalContext.current
	var setupOpen by rememberSaveable { mutableStateOf(!AppSettings.isSetupDone(context)) }
	var setupReopened by rememberSaveable { mutableStateOf(false) }
	val navController = rememberNavController()
	val busy by ShellRunner.busy.collectAsStateWithLifecycle()
	// One ViewModel for the whole app: every destination is a stack entry that
	// comes and goes, so the shared instance outlives them all and screens
	// still own their event collector via HandlePrefsEvents.
	val statusViewModel: StatusViewModel = viewModel()
	val logsViewModel: LogsViewModel = viewModel()
	// Search lives in the shell so the field is pinned in the top bar instead
	// of scrolling away with the list.
	var settingsQuery by rememberSaveable { mutableStateOf("") }
	var settingsSearchOpen by rememberSaveable { mutableStateOf(false) }

	val backStackEntry by navController.currentBackStackEntryAsState()
	val currentRoute = backStackEntry?.destination?.route
	val isSettingsRoot = currentRoute == Routes.SETTINGS

	// Closing a reopened checklist pops back to About; on first launch back
	// exits the app because the handler is disabled.
	BackHandler(enabled = setupOpen && setupReopened) {
		setupOpen = false
		setupReopened = false
	}

	Scaffold(
		modifier = Modifier.fillMaxSize(),
		topBar = {
			Column {
				TopAppBar(
					title = {
						if (isSettingsRoot && settingsSearchOpen) {
							SettingsSearchField(settingsQuery, onQueryChange = { settingsQuery = it })
						} else {
							Text(stringResource(if (setupOpen) R.string.setup_title else Routes.titleOf(currentRoute)))
						}
					},
					navigationIcon = {
						if (!setupOpen && currentRoute != Routes.HOME) {
							IconButton(onClick = { navController.navigateUp() }) {
								Icon(
									painter = painterResource(R.drawable.ic_arrow_back),
									contentDescription = stringResource(R.string.cd_back)
								)
							}
						}
					},
					actions = {
						TopBarActions(
							currentRoute = currentRoute,
							setupOpen = setupOpen,
							searchOpen = settingsSearchOpen,
							logsViewModel = logsViewModel,
							onToggleSearch = {
								settingsSearchOpen = !settingsSearchOpen
								if (!settingsSearchOpen) settingsQuery = ""
							},
							onOpenSettings = { navController.navigate(Routes.SETTINGS) }
						)
					}
				)
				if (busy) {
					LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
				}
			}
		}
	) { inner ->
		Box(Modifier.fillMaxSize().padding(inner)) {
			AppNavHost(
				navController = navController,
				statusViewModel = statusViewModel,
				logsViewModel = logsViewModel,
				settingsQuery = settingsQuery,
				onOpenSetup = {
					setupReopened = true
					setupOpen = true
				}
			)
			if (setupOpen) {
				SetupScreen(
					viewModel(),
					onClose = { completed ->
						if (completed) AppSettings.setSetupDone(context, true)
						setupOpen = false
						setupReopened = false
					}
				)
			}
		}
	}
}

/**
 * The search field that replaces the title while the Settings search is open.
 */
@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
	OutlinedTextField(
		value = query,
		onValueChange = onQueryChange,
		placeholder = { Text(stringResource(R.string.settings_search)) },
		singleLine = true,
		modifier = Modifier.fillMaxWidth()
	)
}

/**
 * Top-bar action set: the Home gear, the Settings search toggle and the Logs
 * copy / share pair. Everything is scoped off [setupOpen] so the checklist
 * overlay never shows a stale action. The copy / share handlers live here so
 * the resource lookup stays configuration-aware inside the composable.
 */
@Composable
private fun TopBarActions(
	currentRoute: String?,
	setupOpen: Boolean,
	searchOpen: Boolean,
	logsViewModel: LogsViewModel,
	onToggleSearch: () -> Unit,
	onOpenSettings: () -> Unit
) {
	val context = LocalContext.current
	val clipboardLabel = stringResource(R.string.clipboard_label)
	if (setupOpen) return
	when (currentRoute) {
		Routes.HOME -> {
			IconButton(onClick = onOpenSettings) {
				Icon(
					painter = painterResource(R.drawable.ic_settings),
					contentDescription = stringResource(R.string.cd_open_settings)
				)
			}
		}
		Routes.SETTINGS -> {
			IconButton(onClick = onToggleSearch) {
				Icon(
					painter = painterResource(if (searchOpen) R.drawable.ic_close else R.drawable.ic_search),
					contentDescription = stringResource(R.string.settings_search)
				)
			}
		}
		Routes.LOGS -> {
			IconButton(
				onClick = {
					val clip = ClipData.newPlainText(clipboardLabel, logsViewModel.lines.value.joinToString("\n"))
					context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
					Toast.makeText(context, R.string.toast_logs_copied, Toast.LENGTH_SHORT).show()
				}
			) {
				Icon(
					painter = painterResource(R.drawable.ic_content_copy),
					contentDescription = stringResource(R.string.action_export_logs)
				)
			}
			IconButton(
				onClick = {
					logsViewModel.shareLogs { intent ->
						if (intent == null) {
							Toast.makeText(context, R.string.toast_cannot_share_logs, Toast.LENGTH_SHORT).show()
						} else {
							context.startActivity(Intent.createChooser(intent, null))
						}
					}
				}
			) {
				Icon(
					painter = painterResource(R.drawable.ic_share),
					contentDescription = stringResource(R.string.action_share_logs)
				)
			}
		}
	}
}

/** The route table: Home, the Settings list, and one destination per category. */
@Composable
private fun AppNavHost(
	navController: NavHostController,
	statusViewModel: StatusViewModel,
	logsViewModel: LogsViewModel,
	settingsQuery: String,
	onOpenSetup: () -> Unit
) {
	// Forward pushes slide in from the right and keep the current screen in
	// place; back pops mirror it. Material's standard stack motion.
	NavHost(
		navController = navController,
		startDestination = Routes.HOME,
		modifier = Modifier.fillMaxSize(),
		enterTransition = { slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn() },
		exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 8 }) + fadeOut() },
		popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 8 }) + fadeIn() },
		popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut() }
	) {
		composable(Routes.HOME) { HomeScreen(statusViewModel, navController::navigate) }
		composable(Routes.SETTINGS) { SettingsRootScreen(statusViewModel, settingsQuery, navController::navigate) }
		composable(Routes.GENERAL) { SettingSectionScreen(statusViewModel, GeneralSection) }
		composable(Routes.APPEARANCE) { AppearanceScreen(statusViewModel) }
		composable(Routes.CUTOUT) { CutoutScreen(statusViewModel) }
		composable(Routes.CLOCK) { ClockScreen(statusViewModel) }
		composable(Routes.BATTERY) { SettingSectionScreen(statusViewModel, BatterySection) }
		composable(Routes.NOTIFICATIONS) { NotificationsScreen(statusViewModel) }
		composable(Routes.CONNECTIVITY) { SettingSectionScreen(statusViewModel, ConnectivitySection) }
		composable(Routes.HIDDEN_APPS) { HiddenAppsScreen(statusViewModel) }
		composable(Routes.LOCK) { LockHubScreen(navController::navigate) }
		composable(Routes.LOCK_APPEARANCE) { LockAppearanceScreen(statusViewModel) }
		composable(Routes.LOCK_BEHAVIOR) { LockBehaviorScreen(statusViewModel) }
		composable(Routes.PROFILES) { ProfilesScreen() }
		composable(Routes.OEM) { OemScreen(statusViewModel) }
		composable(Routes.DEBUG) { DebugHubScreen(navController::navigate) }
		composable(Routes.DEBUG_INFO) { DebugInfoScreen(statusViewModel) }
		composable(Routes.DEBUG_PERMISSIONS) { DebugPermissionsScreen(statusViewModel) }
		composable(Routes.LOGS) { LogsScreen(logsViewModel) }
		composable(Routes.BACKUP) { BackupScreen(statusViewModel) }
		composable(Routes.ABOUT) { AboutScreen(statusViewModel, onOpenSetup) }
	}
}
