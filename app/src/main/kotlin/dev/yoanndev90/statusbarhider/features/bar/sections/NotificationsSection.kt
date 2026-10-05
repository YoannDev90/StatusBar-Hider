package dev.yoanndev90.statusbarhider.features.bar.sections

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSlider
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch

/** Notification icons and the now-playing widget. */
@Composable
fun NotificationsSection(
	prefs: OverlayPrefs,
	vm: PrefsViewModel
) {
	val context = LocalContext.current
	val cannotOpenNotifSettingsToast = stringResource(R.string.toast_cannot_open_notification_settings)

	SettingGroup(R.string.group_notifications) {
		SettingSwitch(R.drawable.ic_notifications, R.string.switch_notification_icons, prefs.showNotifs) { vm.updatePrefs { copy(showNotifs = it) } }
		SettingAction(R.drawable.ic_key, R.string.action_enable_notification_access) {
			try {
				context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
			} catch (_: Exception) {
				Toast
					.makeText(context, cannotOpenNotifSettingsToast, Toast.LENGTH_SHORT)
					.show()
			}
		}
		SettingSlider(R.drawable.ic_filter_9_plus, R.string.slider_max_notification_icons, prefs.maxNotifs, 1, 8) {
			vm.updatePrefs { copy(maxNotifs = it) }
		}
		SettingSwitch(R.drawable.ic_media, R.string.switch_now_playing, prefs.showMedia) { vm.updatePrefs { copy(showMedia = it) } }
	}
}
