package dev.yoanndev90.statusbarhider.ui.screens.settings

import androidx.compose.runtime.Composable
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSubRow
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen
import dev.yoanndev90.statusbarhider.ui.shell.Routes

/** Lock screen hub: two sub-screens, appearance and behavior. */
@Composable
fun LockHubScreen(onNavigate: (String) -> Unit) {
	SettingsScreen {
		item {
			SettingGroup {
				SettingSubRow(R.drawable.ic_palette, R.string.lock_sub_appearance) { onNavigate(Routes.LOCK_APPEARANCE) }
				SettingSubRow(R.drawable.ic_settings, R.string.lock_sub_behavior) { onNavigate(Routes.LOCK_BEHAVIOR) }
			}
		}
	}
}
