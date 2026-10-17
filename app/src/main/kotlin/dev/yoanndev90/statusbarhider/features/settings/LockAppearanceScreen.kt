package dev.yoanndev90.statusbarhider.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.overlay.LockScreenMode
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingRadioRow
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** The layout used on the lock screen: full bar or clock only. */
@Composable
fun LockAppearanceScreen(vm: StatusViewModel) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	HandlePrefsEvents(vm)

	SettingsScreen {
		item {
			SettingGroup {
				SettingRadioRow(
					icon = R.drawable.ic_visibility,
					options =
						listOf(
							R.string.lock_mode_full,
							R.string.lock_mode_clock_only
						),
					selected = if (prefs.lockScreenMode == LockScreenMode.CLOCK_ONLY) 1 else 0,
					onSelect = { index ->
						val mode = if (index == 1) LockScreenMode.CLOCK_ONLY else LockScreenMode.FULL
						vm.updatePrefs { copy(lockScreenMode = mode) }
					}
				)
			}
		}
	}
}
