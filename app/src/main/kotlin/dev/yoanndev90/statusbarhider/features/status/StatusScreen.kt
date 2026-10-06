package dev.yoanndev90.statusbarhider.features.status

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.data.AppSettings
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/**
 * Status tab: who the device is, whether Shizuku is authorized, and the
 * hide / restore commands. App-wide actions live at the bottom.
 */
@Composable
fun StatusScreen(
	vm: StatusViewModel,
	onOpenSetup: () -> Unit = {}
) {
	val state by vm.uiState.collectAsStateWithLifecycle()
	val context = LocalContext.current
	val importFailedToast = stringResource(R.string.toast_import_failed)
	val importOkToast = stringResource(R.string.toast_import_ok)
	// Backup I/O (file + provider stream) runs in the ViewModel's scope: leaving
	// the tab must not cancel a write half-way through.
	val importLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
			if (uri != null) {
				vm.importSettings(uri) { ok ->
					Toast.makeText(context, if (ok) importOkToast else importFailedToast, Toast.LENGTH_LONG).show()
				}
			}
		}
	HandlePrefsEvents(vm)

	SettingsScreen {
		item {
			SettingGroup { DeviceHeader(state) }
		}

		item {
			// First screen, first controls: show / hide without hunting in Style.
			SettingGroup(R.string.section_custom_bar) {
				SettingAction(R.drawable.ic_visibility, R.string.action_show_custom_bar) { vm.showOverlayBar() }
				SettingAction(R.drawable.ic_visibility_off, R.string.action_hide_custom_bar) { vm.setOverlayEnabled(false) }
			}
		}

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

		item {
			SettingGroup(R.string.section_app) {
				SettingAction(R.drawable.ic_key, R.string.action_copy_control_token) { copyControlToken(context) }
				Text(
					text = stringResource(R.string.hint_control_token),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
				)
				SettingAction(R.drawable.ic_settings, R.string.action_open_setup) { onOpenSetup() }
				SettingAction(R.drawable.ic_save_alt, R.string.action_export_settings) {
					vm.exportSettings { intent ->
						if (intent == null) {
							Toast.makeText(context, R.string.toast_export_failed, Toast.LENGTH_LONG).show()
						} else {
							context.startActivity(Intent.createChooser(intent, null))
						}
					}
				}
				SettingAction(R.drawable.ic_upload, R.string.action_import_settings) { importLauncher.launch(arrayOf("*/*")) }
			}
		}
	}
}

/** Copies the control API token so it can be pasted into an adb or Tasker intent. */
private fun copyControlToken(context: Context) {
	val clipboard = context.getSystemService(ClipboardManager::class.java)
	clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), AppSettings.controlToken(context)))
	Toast.makeText(context, R.string.toast_token_copied, Toast.LENGTH_SHORT).show()
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
			text =
				when (state.shizuku) {
					ShizukuState.NOT_RUNNING -> stringResource(R.string.shizuku_not_running)
					ShizukuState.NOT_GRANTED -> stringResource(R.string.shizuku_not_granted)
					ShizukuState.READY -> stringResource(R.string.shizuku_ready)
				},
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
		)
	}
}
