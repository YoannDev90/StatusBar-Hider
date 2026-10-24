package dev.yoanndev90.statusbarhider.core.settings.sections

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.notif.TestNotifier
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingRow
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.Subtitle
import dev.yoanndev90.statusbarhider.core.settings.specs.NotificationSpecs
import dev.yoanndev90.statusbarhider.overlay.NotifListenerService
import dev.yoanndev90.statusbarhider.ui.components.SettingAction

/**
 * Notification icons, the now-playing widget and the test notification:
 * three specs, the listener-access shortcut between them, and the test
 * action alone under its own titled card behind the POST_NOTIFICATIONS gate.
 */
object NotificationSection :
	SettingSection<OverlayPrefs>(
		id = "notifications",
		icon = R.drawable.ic_notifications,
		title = R.string.cat_notifications,
		subtitle = Subtitle.Res(R.string.cat_notifications_sub),
		group = SectionGroup.CUSTOM_BAR,
		keywords = listOf("icons", "media", "now", "playing", "test"),
		rows =
			listOf(
				SettingRow.OfSpec(NotificationSpecs.showNotifs),
				SettingRow.Custom { _, _ -> NotificationAccessRow() },
				SettingRow.OfSpec(NotificationSpecs.maxNotifs),
				SettingRow.OfSpec(NotificationSpecs.showMedia),
				SettingRow.Group(
					title = R.string.section_test,
					rows = listOf(SettingRow.Custom { _, _ -> TestNotificationRow() })
				)
			)
	)

/** Opens the system notification-access screen, reporting a refusal to leave the app. */
@Composable
private fun NotificationAccessRow() {
	val context = LocalContext.current
	val cannotOpenToast = stringResource(R.string.toast_cannot_open_notification_settings)
	SettingAction(R.drawable.ic_key, R.string.action_enable_notification_access) {
		try {
			context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
		} catch (_: Exception) {
			Toast.makeText(context, cannotOpenToast, Toast.LENGTH_SHORT).show()
		}
	}
}

/** Posts the test notification, asking for POST_NOTIFICATIONS first on API 33+. */
@Composable
private fun TestNotificationRow() {
	val context = LocalContext.current
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
