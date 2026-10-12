package dev.yoanndev90.statusbarhider.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.status.OemPicker
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** Device / OEM: the config picker and the hide / restore command set. */
@Composable
fun OemScreen(vm: StatusViewModel) {
	val state by vm.uiState.collectAsStateWithLifecycle()
	HandlePrefsEvents(vm)

	SettingsScreen {
		item {
			SettingGroup(R.string.section_shizuku) {
				SettingAction(R.drawable.ic_adb, R.string.action_authorize_shizuku) { vm.requestShizukuPermission() }
				if (state.shizuku != ShizukuState.READY) {
					Text(
						text = stringResource(R.string.hint_authorize_shizuku),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
					)
				}
			}
		}
		item {
			SettingGroup(R.string.section_status_bar) {
				OemPicker(
					currentId = state.oemId,
					currentName = state.oemName,
					onSelect = vm::selectOem
				)
				SettingAction(R.drawable.ic_refresh, R.string.action_redetect_oem) { vm.redetectOem() }
				SettingAction(R.drawable.ic_hide_source, R.string.action_hide_status_bar) { vm.applyHide() }
				SettingAction(R.drawable.ic_fact_check, R.string.action_check_state) { vm.checkState() }
				SettingAction(R.drawable.ic_restore, R.string.action_restore) { vm.restore() }
			}
		}
	}
}
