package dev.yoanndev90.statusbarhider.features.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.status.StatusUiState
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen
import dev.yoanndev90.statusbarhider.ui.shell.Routes

/**
 * Home: nothing but what the user came for - who the device is, whether the
 * bar is up, and the two buttons. Everything else lives behind the gear.
 */
@Composable
fun HomeScreen(
	vm: StatusViewModel,
	onNavigate: (String) -> Unit
) {
	val state by vm.uiState.collectAsStateWithLifecycle()
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	// The show button can emit GrantOverlayPermission; without the collector
	// the event would be dropped.
	HandlePrefsEvents(vm)

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
			SettingGroup {
				Text(
					text = stringResource(if (prefs.enabled) R.string.home_bar_visible else R.string.home_bar_hidden),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
				)
				SettingAction(R.drawable.ic_visibility, R.string.action_show_custom_bar) { vm.showOverlayBar() }
				SettingAction(R.drawable.ic_visibility_off, R.string.action_hide_custom_bar) { vm.setOverlayEnabled(false) }
			}
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
	}
}

/** Device name, untested badge and Shizuku status. */
@Composable
private fun HomeHeader(state: StatusUiState) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Text(
			text = state.oemName,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(top = 4.dp, bottom = if (state.oemUntested) 0.dp else 8.dp)
		)
		if (state.oemUntested) {
			Text(
				text = stringResource(R.string.header_untested),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.error,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(bottom = 8.dp)
			)
		}
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(
				text =
					when (state.shizuku) {
						ShizukuState.NOT_RUNNING -> stringResource(R.string.shizuku_not_running)
						ShizukuState.NOT_GRANTED -> stringResource(R.string.shizuku_not_granted)
						ShizukuState.READY -> stringResource(R.string.shizuku_ready)
					},
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(end = 6.dp)
			)
			Icon(
				painter = painterResource(R.drawable.ic_chevron_right),
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}
