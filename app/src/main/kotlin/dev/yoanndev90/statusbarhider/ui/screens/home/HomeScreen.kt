package dev.yoanndev90.statusbarhider.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.hide.HideInteractor
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen
import dev.yoanndev90.statusbarhider.ui.screens.status.StatusUiState
import dev.yoanndev90.statusbarhider.ui.screens.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.shell.Routes
import dev.yoanndev90.statusbarhider.ui.state.HandlePrefsEvents

/**
 * Home: the two things the app actually does - hide the system status bar and
 * run the custom bar - each with its live state and one state-aware action,
 * under a status header that opens the permission board.
 */
@Composable
fun HomeScreen(
	vm: StatusViewModel,
	onNavigate: (String) -> Unit
) {
	val context = LocalContext.current
	val state by vm.uiState.collectAsStateWithLifecycle()
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	// The show button can emit GrantOverlayPermission; without the collector
	// the event would be dropped.
	HandlePrefsEvents(vm)

	// The hide flag is persisted, never observed: re-read it when the user
	// comes back (grants and OEM picks happen in other screens) and on the
	// falling edge of the shell busy flag, which is the moment a run writes
	// it. Both bumps are the same key of that read.
	var refreshTick by remember { mutableStateOf(0) }
	LifecycleResumeEffect(Unit) {
		refreshTick++
		onPauseOrDispose { }
	}
	val busy by ShellRunner.busy.collectAsStateWithLifecycle()
	var wasBusy by remember { mutableStateOf(busy) }
	LaunchedEffect(busy) {
		if (wasBusy && !busy) refreshTick++
		wasBusy = busy
	}
	val hidden = remember(refreshTick) { HideInteractor.isHidden(context) }

	SettingsScreen {
		item {
			// The status header is the shortcut to the permission board.
			SettingGroup(
				modifier =
					Modifier
						.clickable { onNavigate(Routes.DEBUG_PERMISSIONS) }
			) { HomeHeader(state) }
		}
		item {
			// Shizuku is the one grant worth surfacing on Home; the rest lives
			// in the permission board behind the status header.
			if (state.shizuku != ShizukuState.READY) {
				SettingGroup {
					SettingAction(R.drawable.ic_adb, R.string.action_authorize_shizuku) { vm.requestShizukuPermission() }
				}
			}
		}
		item {
			SettingGroup(R.string.section_status_bar) {
				StateLine(
					value = stringResource(if (hidden) R.string.home_value_hidden else R.string.home_value_visible),
					color = if (hidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
				)
				if (hidden) {
					SettingAction(R.drawable.ic_restore, R.string.action_restore, danger = true) { vm.restore() }
				} else {
					SettingAction(R.drawable.ic_hide_source, R.string.action_hide_status_bar) { vm.applyHide() }
				}
			}
		}
		item {
			SettingGroup(R.string.section_custom_bar) {
				StateLine(
					value = stringResource(if (prefs.enabled) R.string.home_value_visible else R.string.home_value_hidden),
					color = if (prefs.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
				)
				if (prefs.enabled) {
					SettingAction(R.drawable.ic_visibility_off, R.string.action_hide_custom_bar) { vm.setOverlayEnabled(false) }
				} else {
					SettingAction(R.drawable.ic_visibility, R.string.action_show_custom_bar) { vm.showOverlayBar() }
				}
			}
		}
	}
}

/** Device identity, untested badge and Shizuku status; the whole card taps through. */
@Composable
private fun HomeHeader(state: StatusUiState) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			StatusRow(
				label = R.string.home_device,
				value = state.oemName,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			StatusRow(
				label = R.string.perm_shizuku,
				value =
					stringResource(
						when (state.shizuku) {
							ShizukuState.READY -> R.string.home_value_ready
							ShizukuState.NOT_GRANTED -> R.string.home_shizuku_waiting
							ShizukuState.NOT_RUNNING -> R.string.home_shizuku_off
						}
					),
				color = if (state.shizuku == ShizukuState.READY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
			)
			if (state.oemUntested) {
				Text(
					text = stringResource(R.string.header_untested),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.error,
					modifier = Modifier.padding(top = 4.dp)
				)
			}
		}
		Icon(
			painter = painterResource(R.drawable.ic_chevron_right),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(20.dp)
		)
	}
}

/** Read-only status line: label on the left, dot + value on the right. */
@Composable
private fun StatusRow(
	@StringRes label: Int,
	value: String,
	color: Color
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = stringResource(label),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.weight(1f).padding(end = 12.dp)
		)
		StatusDot(color)
		Spacer(Modifier.width(6.dp))
		Text(
			text = value,
			style = MaterialTheme.typography.bodyMedium,
			color = color
		)
	}
}

/** A card's headline state: coloured dot and value at title size. */
@Composable
private fun StateLine(
	value: String,
	color: Color
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		StatusDot(color, size = 10.dp)
		Spacer(Modifier.width(10.dp))
		Text(
			text = value,
			style = MaterialTheme.typography.titleLarge,
			color = color
		)
	}
}

/** The status bullet; decorative, the value next to it carries the meaning. */
@Composable
private fun StatusDot(
	color: Color,
	size: Dp = 8.dp
) {
	Box(
		modifier =
			Modifier
				.size(size)
				.background(color = color, shape = CircleShape)
	)
}
