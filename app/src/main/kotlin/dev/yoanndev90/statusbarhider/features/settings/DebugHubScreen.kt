package dev.yoanndev90.statusbarhider.features.settings

import androidx.compose.runtime.Composable
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSubRow
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen
import dev.yoanndev90.statusbarhider.ui.shell.Routes

/** Debug hub: device info and the permission status board. */
@Composable
fun DebugHubScreen(onNavigate: (String) -> Unit) {
	SettingsScreen {
		item {
			SettingGroup {
				SettingSubRow(R.drawable.ic_info, R.string.debug_sub_info) { onNavigate(Routes.DEBUG_INFO) }
				SettingSubRow(R.drawable.ic_check_circle, R.string.debug_sub_permissions) { onNavigate(Routes.DEBUG_PERMISSIONS) }
			}
		}
	}
}
