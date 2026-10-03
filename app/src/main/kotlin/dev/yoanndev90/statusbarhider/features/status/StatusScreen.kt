package dev.yoanndev90.statusbarhider.features.status

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.MainActivity
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/**
 * Status tab: who the device is, whether Shizuku is authorized, and the
 * hide / restore commands. App-wide actions live at the bottom.
 */
@Composable
fun StatusScreen(vm: StatusViewModel) {
	val state by vm.uiState.collectAsStateWithLifecycle()
	val context = LocalContext.current

	SettingsScreen {
		SettingGroup { DeviceHeader(state) }

		SettingGroup(R.string.section_shizuku) {
			SettingAction(R.string.action_authorize_shizuku) { vm.requestShizukuPermission() }
			if (state.shizuku != ShizukuState.READY) {
				Text(
					text = stringResource(R.string.hint_authorize_shizuku),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
				)
			}
		}

		SettingGroup(R.string.section_status_bar) {
			OemPicker(
				currentId = state.oemId,
				currentName = state.oemName,
				onSelect = vm::selectOem
			)
			SettingAction(R.string.action_redetect_oem) { vm.redetectOem() }
			SettingAction(R.string.action_hide_status_bar) { vm.applyHide() }
			SettingAction(R.string.action_check_state) { vm.checkState() }
			SettingAction(R.string.action_restore) { vm.restore() }
		}

		SettingGroup(R.string.section_app) {
			SettingAction(R.string.action_hide_from_launcher) {
				MainActivity.hideFromLauncher(context)
				Toast.makeText(context, R.string.toast_hidden_from_launcher, Toast.LENGTH_LONG).show()
			}
		}
	}
}

/** Device name, untested badge and Shizuku status. */
@Composable
private fun DeviceHeader(state: StatusUiState) {
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
		Text(
			text = state.shizukuText,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.fillMaxWidth()
		)
	}
}
