package dev.yoanndev90.statusbarhider.features.style.sections

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.notif.TestNotifier
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.overlay.NotifListenerService
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch

/** Test affordances: poke the notification widgets and the camera ring. */
@Composable
fun TestSection(
	prefs: OverlayPrefs,
	vm: PrefsViewModel
) {
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
		SettingSwitch(R.drawable.ic_preview, R.string.switch_ring_preview, prefs.cameraRingPreview) { on ->
			// Flag first: when the bar still has to be started (or the overlay
			// permission granted), the preview must already be in the state.
			vm.updatePrefs { copy(cameraRingPreview = on) }
			if (on) vm.showOverlayBar()
		}
		Text(
			text = stringResource(R.string.hint_ring_preview),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
		)
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
