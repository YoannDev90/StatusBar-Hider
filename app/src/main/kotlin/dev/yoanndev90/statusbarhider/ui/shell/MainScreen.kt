package dev.yoanndev90.statusbarhider.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.data.AppSettings
import dev.yoanndev90.statusbarhider.features.bar.BarScreen
import dev.yoanndev90.statusbarhider.features.logs.LogsScreen
import dev.yoanndev90.statusbarhider.features.logs.LogsViewModel
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.features.status.StatusScreen
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.features.style.StyleScreen
import dev.yoanndev90.statusbarhider.ui.setup.SetupScreen

/**
 * Shell of the app: a top bar titled after the current tab, a bottom
 * navigation bar over four destinations, and a tab host that keeps every
 * visited tab composed.
 *
 * Navigation Compose disposed the destination it left, so each switch threw
 * the target page away and built it again: a full recomposition, a full
 * relayout - every Text of the page was measured from scratch - and a full
 * redraw, which the trace showed as an ~80 ms UI thread frame on the test
 * device. Here a tab is composed the first time it is opened and then stays
 * alive; only the selected one is drawn and hit tested, the others sit behind
 * [hiddenLayer] with their measured state intact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
	val context = LocalContext.current
	// First launch (or "Not now" on a previous run) opens the checklist instead
	// of the shell; later opens come from the Status tab.
	var setupOpen by rememberSaveable { mutableStateOf(!AppSettings.isSetupDone(context)) }
	var setupReopened by rememberSaveable { mutableStateOf(false) }
	var current by rememberSaveable { mutableStateOf(AppDestination.START) }
	// Bitmask of the tabs composed so far, so a restored process rebuilds the
	// tab it was on plus nothing else.
	var visitedMask by rememberSaveable { mutableStateOf(1 shl AppDestination.START.ordinal) }
	val busy by ShellRunner.busy.collectAsStateWithLifecycle()
	// One ViewModel per tab - the instance each screen has always owned: every
	// screen collects the one-shot events of its own ViewModel, so tabs sharing
	// an instance would each answer the same event.
	val statusViewModel: StatusViewModel = viewModel()
	val barViewModel: PrefsViewModel = viewModel(key = "bar")
	val styleViewModel: PrefsViewModel = viewModel(key = "style")
	val logsViewModel: LogsViewModel = viewModel()

	// The checklist is the start destination on first launch, where back exits
	// the app as usual; re-opened from the Status tab it goes back there.
	BackHandler(enabled = setupReopened || (!setupOpen && current != AppDestination.START)) {
		if (setupOpen) {
			setupOpen = false
			setupReopened = false
		} else {
			current = AppDestination.START
		}
	}

	Scaffold(
		modifier = Modifier.fillMaxSize(),
		topBar = {
			Column {
				TopAppBar(
					title = {
						Text(
							stringResource(if (setupOpen) R.string.setup_title else current.titleRes)
						)
					}
				)
				if (busy) {
					LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
				}
			}
		},
		bottomBar = {
			if (!setupOpen) {
				NavigationBar {
					AppDestination.entries.forEach { destination ->
						NavigationBarItem(
							selected = destination == current,
							onClick = {
								visitedMask = visitedMask or (1 shl destination.ordinal)
								current = destination
							},
							icon = { Icon(painter = painterResource(destination.iconRes), contentDescription = null) },
							label = { Text(stringResource(destination.labelRes)) }
						)
					}
				}
			}
		}
	) { inner ->
		Box(Modifier.fillMaxSize().padding(inner)) {
			AppDestination.entries.forEach { destination ->
				val visited =
					destination == current || (visitedMask and (1 shl destination.ordinal)) != 0
				if (visited) {
					key(destination) {
						val selected = !setupOpen && destination == current
						Box(
							modifier =
								Modifier
									.fillMaxSize()
									.zIndex(if (selected) 1f else 0f)
									.then(if (selected) Modifier else Modifier.hiddenLayer)
						) {
							when (destination) {
								AppDestination.STATUS ->
									StatusScreen(statusViewModel, onOpenSetup = {
										setupReopened = true
										setupOpen = true
									})
								AppDestination.BAR -> BarScreen(barViewModel)
								AppDestination.STYLE -> StyleScreen(styleViewModel)
								AppDestination.LOG -> LogsScreen(logsViewModel)
							}
						}
					}
				}
			}
			if (setupOpen) {
				SetupScreen(
					viewModel(),
					onClose = { completed ->
						if (completed) AppSettings.setSetupDone(context, true)
						setupOpen = false
						setupReopened = false
						current = AppDestination.START
					}
				)
			}
		}
	}
}

/**
 * Keeps a tab composed and measured but out of the frame: its subtree is never
 * drawn, and taps meant for the selected tab cannot fall through to it.
 */
private val Modifier.hiddenLayer: Modifier
	get() =
		this
			.drawWithContent { }
			.pointerInput(Unit) {
				awaitPointerEventScope {
					while (true) {
						awaitPointerEvent().changes.forEach { it.consume() }
					}
				}
			}
