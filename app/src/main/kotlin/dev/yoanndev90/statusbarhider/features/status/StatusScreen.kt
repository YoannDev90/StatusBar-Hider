package dev.yoanndev90.statusbarhider.features.status

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.backup.SettingsBackup
import dev.yoanndev90.statusbarhider.core.backup.SettingsBackupException
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.data.AppSettings
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
	// Backup I/O (file + provider stream) runs on IO; the scope dies with the screen.
	val uiScope = rememberCoroutineScope()
	val importLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
			if (uri != null) uiScope.launch { importSettings(context, uri, importOkToast, importFailedToast) }
		}
	HandlePrefsEvents(vm)

	SettingsScreen {
		SettingGroup { DeviceHeader(state) }

		// First screen, first controls: show / hide without hunting in Style.
		SettingGroup(R.string.section_custom_bar) {
			SettingAction(R.drawable.ic_visibility, R.string.action_show_custom_bar) { vm.showOverlayBar() }
			SettingAction(R.drawable.ic_visibility_off, R.string.action_hide_custom_bar) { vm.setOverlayEnabled(false) }
		}

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

		SettingGroup(R.string.section_app) {
			SettingAction(R.drawable.ic_key, R.string.action_copy_control_token) { copyControlToken(context) }
			Text(
				text = stringResource(R.string.hint_control_token),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
			)
			SettingAction(R.drawable.ic_settings, R.string.action_open_setup) { onOpenSetup() }
			SettingAction(R.drawable.ic_save_alt, R.string.action_export_settings) { uiScope.launch { exportSettings(context) } }
			SettingAction(R.drawable.ic_upload, R.string.action_import_settings) { importLauncher.launch(arrayOf("*/*")) }
		}
	}
}

/** Copies the control API token so it can be pasted into an adb or Tasker intent. */
private fun copyControlToken(context: Context) {
	val clipboard = context.getSystemService(ClipboardManager::class.java)
	clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), AppSettings.controlToken(context)))
	Toast.makeText(context, R.string.toast_token_copied, Toast.LENGTH_SHORT).show()
}

/** Shares the current settings as a JSON file through the system share sheet. */
private suspend fun exportSettings(context: Context) {
	val intent = withContext(Dispatchers.IO) { SettingsBackup.shareIntent(context) }
	if (intent == null) {
		Toast.makeText(context, R.string.toast_export_failed, Toast.LENGTH_LONG).show()
		return
	}
	context.startActivity(Intent.createChooser(intent, null))
	LogStore.append(context, context.getString(R.string.log_settings_exported))
}

/** Reads a picked file and applies it as a settings backup. */
private suspend fun importSettings(
	context: Context,
	uri: Uri,
	okToast: String,
	failToast: String
) {
	try {
		val raw =
			withContext(Dispatchers.IO) {
				context.contentResolver
					.openInputStream(uri)
					?.bufferedReader()
					?.use { it.readText() }
					?: throw SettingsBackupException("Cannot read ${uri.lastPathSegment}")
			}
		// Parse + apply as one uninterruptible block: a cancelled scope must
		// not leave the settings half-swapped.
		withContext(Dispatchers.IO) { SettingsBackup.importSettings(context, raw) }
		LogStore.append(context, context.getString(R.string.log_settings_imported))
		Toast.makeText(context, okToast, Toast.LENGTH_LONG).show()
	} catch (e: CancellationException) {
		throw e
	} catch (e: Exception) {
		LogStore.append(context, context.getString(R.string.log_error, e.message))
		Toast.makeText(context, failToast, Toast.LENGTH_LONG).show()
	}
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
