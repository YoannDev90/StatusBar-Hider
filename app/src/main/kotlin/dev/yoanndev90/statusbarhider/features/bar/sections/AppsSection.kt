package dev.yoanndev90.statusbarhider.features.bar.sections

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
	// Querying and labelling every installed app is a few hundred binder calls:
	// run once per process, off the main thread, and never per tab visit.
	val apps by produceState(AppListCache.peek()) {
		if (value == null) {
			value = withContext(Dispatchers.IO) { AppListCache.load(context) }
		}
	}
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
					prefs.hiddenApps.joinToString(", ") { pkg -> apps?.firstOrNull { it.pkg == pkg }?.label ?: pkg }
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
	apps: List<AppEntry>?,
	selected: Set<String>,
	onDismiss: () -> Unit,
	onConfirm: (List<String>) -> Unit
) {
	var choice by remember(selected) { mutableStateOf(selected) }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.dialog_pick_apps)) },
		text = {
			if (apps == null) {
				// First open while the background query is still running: keep the
				// dialog up instead of flashing an empty list.
				Box(modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp), contentAlignment = Alignment.Center) {
					CircularProgressIndicator()
				}
			} else {
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

/**
 * App icon at a fixed raster size, decoded off the main thread.
 *
 * Rasterising the full-resolution adaptive icon inside composition dropped a
 * frame for every row that scrolled into view, and leaving the composition
 * threw the bitmap away, so scrolling back decoded it again. The slot is always
 * laid out (even for a missing icon) so a late bitmap cannot shift the row.
 */
@Composable
private fun AppIcon(
	pkg: String,
	modifier: Modifier
) {
	val pm = LocalContext.current.packageManager
	val bitmap by produceState(AppIconCache[pkg]) {
		if (value == null) {
			value =
				withContext(Dispatchers.IO) {
					runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }
						.getOrNull()
						?.also { AppIconCache[pkg] = it }
				}
		}
	}
	Box(modifier = modifier) {
		bitmap?.let {
			Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize())
		}
	}
}

/**
 * LRU of decoded icons, big enough for the visible page of the picker and then
 * some (128 x 96 x 96 px is about 4.5 MB of heap).
 */
private object AppIconCache {
	private const val MAX_ENTRIES = 128

	private val lock = Any()

	/** Access-ordered, so the first key is the least recently used one. */
	private val entries = LinkedHashMap<String, ImageBitmap>(16, 0.75f, true)

	operator fun get(pkg: String): ImageBitmap? = synchronized(lock) { entries[pkg] }

	operator fun set(
		pkg: String,
		bitmap: ImageBitmap
	) {
		synchronized(lock) {
			entries[pkg] = bitmap
			while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
		}
	}
}

/**
 * Process-wide launcher list.
 *
 * [query] is a few hundred binder calls (one `loadLabel` per app, each able to
 * build a `Resources` for another package), so it is computed at most once per
 * process and never on the main thread. A plain `remember` re-ran it every time
 * the Bar tab left and re-entered composition, freezing the tab switch.
 */
private object AppListCache {
	@Volatile
	private var cached: List<AppEntry>? = null

	/** The list already in memory, or null while the first load has not finished. */
	fun peek(): List<AppEntry>? = cached

	/** [cached], or the result of a single [query]; failures are not cached. */
	fun load(context: Context): List<AppEntry>? =
		cached ?: query(context).also { loaded -> if (loaded.isNotEmpty()) cached = loaded }

	/** Launchable apps sorted by label, without the app itself. */
	private fun query(context: Context): List<AppEntry> {
		val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
		return try {
			context.packageManager
				.queryIntentActivities(intent, 0)
				.map { AppEntry(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
				.distinctBy { it.pkg }
				.filter { it.pkg != context.packageName }
				.sortedBy { it.label.lowercase() }
		} catch (e: Exception) {
			Log.w(TAG, "query", e)
			LogStore.appendOnce(
				context,
				"$TAG#query",
				context.getString(R.string.log_error, "launcher apps: ${e.message}")
			)
			emptyList()
		}
	}
}
