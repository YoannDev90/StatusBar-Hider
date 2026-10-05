package dev.yoanndev90.statusbarhider.features.shared

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.yoanndev90.statusbarhider.R

/**
 * Renders the one-shot [PrefsEvent]s of [vm]: toasts, settings pages and
 * anything else that needs a Context, kept out of the ViewModel.
 *
 * Call once per screen for the ViewModel instance that screen owns.
 */
@Composable
fun HandlePrefsEvents(vm: PrefsViewModel) {
	val context = LocalContext.current
	LaunchedEffect(vm) {
		vm.events.collect { event ->
			when (event) {
				PrefsEvent.GrantOverlayPermission -> {
					Toast.makeText(context, R.string.toast_grant_overlay_permission, Toast.LENGTH_LONG).show()
					val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
					context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
				}
			}
		}
	}
}
