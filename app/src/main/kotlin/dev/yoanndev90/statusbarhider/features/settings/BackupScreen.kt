package dev.yoanndev90.statusbarhider.features.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** Backup: export the settings as JSON, import a file, pull from the sister build, or reset. */
@Composable
fun BackupScreen(vm: StatusViewModel) {
	val context = LocalContext.current
	val importFailedToast = stringResource(R.string.toast_import_failed)
	val importOkToast = stringResource(R.string.toast_import_ok)
	val sisterLabel by vm.sisterLabel.collectAsStateWithLifecycle()
	var showSisterDialog by rememberSaveable { mutableStateOf(false) }
	var showResetDialog by rememberSaveable { mutableStateOf(false) }
	// Backup I/O (file + provider stream) runs in the ViewModel's scope: leaving
	// the screen must not cancel a write half-way through.
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
			SettingGroup {
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
				// Only offered when the other build (debug <-> release) is installed too.
				sisterLabel?.let {
					SettingAction(R.drawable.ic_sync, R.string.action_import_sister) { showSisterDialog = true }
				}
			}
		}
		item {
			SettingGroup {
				SettingAction(R.drawable.ic_restore, R.string.action_reset_settings, danger = true) { showResetDialog = true }
			}
		}
	}

	// Confirmation before the pull: one tap would otherwise replace every setting.
	sisterLabel?.let { label ->
		if (showSisterDialog) {
			SisterImportDialog(
				label = label,
				onDismiss = { showSisterDialog = false },
				onConfirm = {
					showSisterDialog = false
					vm.importFromSister { ok ->
						Toast.makeText(context, if (ok) importOkToast else importFailedToast, Toast.LENGTH_LONG).show()
					}
				}
			)
		}
	}

	if (showResetDialog) {
		AlertDialog(
			onDismissRequest = { showResetDialog = false },
			title = { Text(stringResource(R.string.dialog_reset_title)) },
			text = { Text(stringResource(R.string.dialog_reset_text)) },
			confirmButton = {
				TextButton(
					onClick = {
						showResetDialog = false
						vm.resetToDefaults()
					}
				) { Text(stringResource(R.string.action_reset_settings)) }
			},
			dismissButton = {
				TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.action_cancel)) }
			}
		)
	}
}

/** Asks before pulling the sister build's settings; [label] is its installed app name. */
@Composable
private fun SisterImportDialog(
	label: String,
	onDismiss: () -> Unit,
	onConfirm: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.dialog_import_sister_title)) },
		text = { Text(stringResource(R.string.dialog_import_sister, label)) },
		confirmButton = {
			TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_import_settings)) }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
		}
	)
}
