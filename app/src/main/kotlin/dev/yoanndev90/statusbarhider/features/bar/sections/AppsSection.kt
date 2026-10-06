package dev.yoanndev90.statusbarhider.features.bar.sections

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.usage.UsageAccess
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch

private const val TAG = "AppsSection"

/** One launchable app shown in the picker (own package excluded). */
private data class AppEntry(
	val pkg: String,
	val label: String
)

/** Apps the custom bar must stay out of: camera viewfinders, video players, games... */
@Composable
fun AppsSection(
	prefs: OverlayPrefs,
	vm: PrefsViewModel
) {
	val context = LocalContext.current
	val errorFormat = stringResource(R.string.log_error)
	val apps = remember { launcherApps(context) }
	// saveable: reopening the app list is less jarring than closing it on rotation.
	var pickerOpen by rememberSaveable { mutableStateOf(false) }
	// Re-read on every resume: the grant happens in another screen.
	var usageGranted by remember { mutableStateOf(UsageAccess.granted(context)) }
	LifecycleResumeEffect(Unit) {
		usageGranted = UsageAccess.granted(context)
		onPauseOrDispose { }
	}

	SettingGroup(R.string.group_apps) {
		SettingSwitch(R.drawable.ic_app_blocking, R.string.switch_hide_bar_in_apps, prefs.hideBarInApps) {
			vm.updatePrefs { copy(hideBarInApps = it) }
		}
		if (!usageGranted) {
			Text(
				text = stringResource(R.string.hint_usage_access),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
			)
			SettingAction(R.drawable.ic_query_stats, R.string.action_grant_usage_access) {
				try {
					context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
				} catch (e: Exception) {
					Log.w(TAG, "usage settings", e)
					LogStore.appendOnce(
						context,
						"$TAG#usageSettings",
						String.format(errorFormat, "usage settings: ${e.message}")
					)
				}
			}
		}
		SettingAction(R.drawable.ic_apps, R.string.action_choose_apps) { pickerOpen = true }
		Text(
			text =
				if (prefs.hiddenApps.isEmpty()) {
					stringResource(R.string.hint_no_apps_selected)
				} else {
					prefs.hiddenApps.joinToString(", ") { pkg -> apps.firstOrNull { it.pkg == pkg }?.label ?: pkg }
				},
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
		)
	}

	if (pickerOpen) {
		AppPickerDialog(
			apps = apps,
			selected = prefs.hiddenApps.toSet(),
			onDismiss = { pickerOpen = false },
			onConfirm = { picked ->
				vm.updatePrefs { copy(hiddenApps = picked) }
				pickerOpen = false
			}
		)
	}
}

/** Multi-select list of launcher apps; the result is persisted as package names. */
@Composable
private fun AppPickerDialog(
	apps: List<AppEntry>,
	selected: Set<String>,
	onDismiss: () -> Unit,
	onConfirm: (List<String>) -> Unit
) {
	var choice by remember(selected) { mutableStateOf(selected) }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.dialog_pick_apps)) },
		text = {
			LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
				items(apps, key = { it.pkg }) { app ->
					val checked = app.pkg in choice
					Row(
						modifier =
							Modifier
								.fillMaxWidth()
								.clickable {
									choice = if (checked) choice - app.pkg else choice + app.pkg
								}.padding(vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						AppIcon(app.pkg, Modifier.padding(end = 12.dp).size(32.dp))
						Column(modifier = Modifier.weight(1f)) {
							Text(text = app.label, style = MaterialTheme.typography.bodyMedium)
							Text(
								text = app.pkg,
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Checkbox(checked = checked, onCheckedChange = null)
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = { onConfirm(choice.toList()) }) { Text(stringResource(R.string.action_save)) }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
		}
	)
}

/** App icon at a fixed raster size; missing icons simply leave the slot empty. */
@Composable
private fun AppIcon(
	pkg: String,
	modifier: Modifier
) {
	val pm = LocalContext.current.packageManager
	val bitmap = remember(pkg) { runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull() }
	if (bitmap != null) {
		Image(
			bitmap = bitmap.asImageBitmap(),
			contentDescription = null,
			modifier = modifier
		)
	}
}

/** Launchable apps sorted by label, without the app itself. */
private fun launcherApps(context: Context): List<AppEntry> {
	val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
	return try {
		context.packageManager
			.queryIntentActivities(intent, 0)
			.map { AppEntry(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
			.distinctBy { it.pkg }
			.filter { it.pkg != context.packageName }
			.sortedBy { it.label.lowercase() }
	} catch (e: Exception) {
		Log.w(TAG, "launcherApps", e)
		LogStore.appendOnce(
			context,
			"$TAG#launcherApps",
			context.getString(R.string.log_error, "launcher apps: ${e.message}")
		)
		emptyList()
	}
}
