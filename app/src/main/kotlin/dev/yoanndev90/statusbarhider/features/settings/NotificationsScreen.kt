package dev.yoanndev90.statusbarhider.features.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.notif.TestNotifier
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.overlay.NotifListenerService
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSlider
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** Notification icons, the now-playing widget and the test notification. */
@Composable
fun NotificationsScreen(vm: StatusViewModel) {
	val context = LocalContext.current
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	val cannotOpenNotifSettingsToast = stringResource(R.string.toast_cannot_open_notification_settings)
	val sentOk = stringResource(R.string.toast_test_notif_sent)
	val sentNoListener = stringResource(R.string.toast_test_notif_no_listener)
	val denied = stringResource(R.string.toast_test_notif_denied)
	val permissionLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
			if (granted) {
				postTest(context, sentOk, sentNoListener)
			} else {
				Toast.makeText(context, denied, Toast.LENGTH_LONG).show()
			}
		}
	HandlePrefsEvents(vm)

	SettingsScreen {
		item {
			SettingGroup {
				SettingSwitch(R.drawable.ic_notifications, R.string.switch_notification_icons, prefs.showNotifs) {
					vm.updatePrefs { copy(showNotifs = it) }
				}
				SettingAction(R.drawable.ic_key, R.string.action_enable_notification_access) {
					try {
						context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
					} catch (_: Exception) {
						Toast.makeText(context, cannotOpenNotifSettingsToast, Toast.LENGTH_SHORT).show()
					}
				}
				SettingSlider(R.drawable.ic_filter_9_plus, R.string.slider_max_notification_icons, prefs.maxNotifs, 1, 8) {
					vm.updatePrefs { copy(maxNotifs = it) }
				}
				SettingSwitch(R.drawable.ic_media, R.string.switch_now_playing, prefs.showMedia) { vm.updatePrefs { copy(showMedia = it) } }
			}
		}
		item {
			SettingGroup(R.string.section_test) {
				SettingAction(R.drawable.ic_campaign, R.string.action_test_notification) {
					if (Build.VERSION.SDK_INT >= 33 &&
						context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
					) {
						permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
					} else {
						postTest(context, sentOk, sentNoListener)
					}
				}
			}
		}
	}
}

/** Posts the test notification and reports where it will (or will not) show up. */
private fun postTest(
	context: Context,
	okMessage: String,
	noListenerMessage: String
) {
	val sent = TestNotifier.send(context)
	val message =
		when {
			!sent -> context.getString(R.string.toast_test_notif_failed)
			!NotifListenerService.isEnabled(context) -> noListenerMessage
			else -> okMessage
		}
	Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
