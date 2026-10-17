package dev.yoanndev90.statusbarhider.features.settings

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.permission.PermissionAccess
import dev.yoanndev90.statusbarhider.core.permission.PermissionGrants
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayBackground
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingNavRow
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen
import dev.yoanndev90.statusbarhider.ui.shell.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One row of the Settings list: where it points and how it reads. */
private data class Category(
	@DrawableRes val icon: Int,
	@StringRes val title: Int,
	@StringRes val subtitle: Int,
	val route: String,
	/** Group the row belongs to: bar settings first, app-level ones second. */
	val group: Int,
	/** Extra search terms; title and subtitle are always matched too. */
	val keywords: List<String> = emptyList(),
	/** Sub-screens are searchable but not listed on the root screen. */
	val listed: Boolean = true
)

/** A [Category] with its strings resolved, so search does not re-resolve them per keystroke. */
private data class ResolvedCategory(
	val icon: Int,
	val title: String,
	val subtitle: String,
	val route: String,
	val group: Int,
	val listed: Boolean,
	/** Lowercase haystack: title, subtitle and keywords. */
	val searchIndex: String
)

private val categories =
	listOf(
		Category(
			R.drawable.ic_settings,
			R.string.cat_general,
			R.string.cat_general_sub,
			Routes.GENERAL,
			R.string.section_custom_bar,
			listOf("refresh boot touch polling burn-in interval auto-hide")
		),
		Category(
			R.drawable.ic_palette,
			R.string.cat_appearance,
			R.string.cat_appearance_sub,
			Routes.APPEARANCE,
			R.string.section_custom_bar,
			listOf("background transparent semi black text size padding weight widget order")
		),
		Category(
			R.drawable.ic_crop_free,
			R.string.cat_cutout,
			R.string.cat_cutout_sub,
			Routes.CUTOUT,
			R.string.section_custom_bar,
			listOf("notch camera ring color position")
		),
		Category(
			R.drawable.ic_schedule,
			R.string.cat_clock,
			R.string.cat_clock_sub,
			Routes.CLOCK,
			R.string.section_custom_bar,
			listOf("time seconds 24h date format alarm calendar")
		),
		Category(
			R.drawable.ic_battery_std,
			R.string.cat_battery,
			R.string.cat_battery_sub,
			Routes.BATTERY,
			R.string.section_custom_bar,
			listOf("level percent icon")
		),
		Category(
			R.drawable.ic_notifications,
			R.string.cat_notifications,
			R.string.cat_notifications_sub,
			Routes.NOTIFICATIONS,
			R.string.section_custom_bar,
			listOf("icons media now playing test")
		),
		Category(
			R.drawable.ic_wifi,
			R.string.cat_connectivity,
			R.string.cat_connectivity_sub,
			Routes.CONNECTIVITY,
			R.string.section_custom_bar,
			listOf("wifi bluetooth airplane vpn hotspot bandwidth nfc gps dnd")
		),
		Category(
			R.drawable.ic_apps,
			R.string.cat_hidden_apps,
			R.string.cat_hidden_apps_sub,
			Routes.HIDDEN_APPS,
			R.string.section_custom_bar,
			listOf("blacklist exclude apps usage")
		),
		Category(
			R.drawable.ic_lock,
			R.string.cat_lock_screen,
			R.string.cat_lock_screen_sub,
			Routes.LOCK,
			R.string.section_custom_bar,
			listOf("keyguard locked")
		),
		Category(
			R.drawable.ic_palette,
			R.string.lock_sub_appearance,
			R.string.lock_sub_appearance_sub,
			Routes.LOCK_APPEARANCE,
			R.string.section_custom_bar,
			listOf("lock keyguard layout full clock only"),
			listed = false
		),
		Category(
			R.drawable.ic_settings,
			R.string.lock_sub_behavior,
			R.string.lock_sub_behavior_sub,
			Routes.LOCK_BEHAVIOR,
			R.string.section_custom_bar,
			listOf("lock keyguard visibility overlay accessibility hide notifications"),
			listed = false
		),
		Category(R.drawable.ic_person, R.string.cat_profiles, R.string.cat_profiles_sub, Routes.PROFILES, R.string.section_app_group),
		Category(
			R.drawable.ic_devices,
			R.string.cat_oem,
			R.string.cat_oem_sub,
			Routes.OEM,
			R.string.section_app_group,
			listOf("shizuku hide restore status bar sony samsung xiaomi")
		),
		Category(
			R.drawable.ic_bug_report,
			R.string.cat_debug,
			R.string.cat_debug_sub,
			Routes.DEBUG,
			R.string.section_app_group,
			listOf("device permissions")
		),
		Category(
			R.drawable.ic_info,
			R.string.debug_sub_info,
			R.string.debug_sub_info_sub,
			Routes.DEBUG_INFO,
			R.string.section_app_group,
			listOf("device model manufacturer version sdk build"),
			listed = false
		),
		Category(
			R.drawable.ic_check_circle,
			R.string.debug_sub_permissions,
			R.string.debug_sub_permissions_sub,
			Routes.DEBUG_PERMISSIONS,
			R.string.section_app_group,
			listOf("permission shizuku overlay calendar accessibility grant usage notification"),
			listed = false
		),
		Category(
			R.drawable.ic_nav_log,
			R.string.cat_logs,
			R.string.cat_logs_sub,
			Routes.LOGS,
			R.string.section_app_group,
			listOf("log output command debug")
		),
		Category(
			R.drawable.ic_save_alt,
			R.string.cat_backup,
			R.string.cat_backup_sub,
			Routes.BACKUP,
			R.string.section_app_group,
			listOf("export import json")
		),
		Category(
			R.drawable.ic_info,
			R.string.cat_about,
			R.string.cat_about_sub,
			Routes.ABOUT,
			R.string.section_app_group,
			listOf("token version setup")
		)
	)

/** Resolves every category title, subtitle and keyword haystack once per composition. */
@Composable
private fun rememberResolvedCategories(): List<ResolvedCategory> {
	val context = LocalContext.current
	return remember(context) { resolveCategories(context) }
}

private fun resolveCategories(context: Context): List<ResolvedCategory> =
	categories.map { category ->
		val title = context.getString(category.title)
		val subtitle = context.getString(category.subtitle)
		ResolvedCategory(
			icon = category.icon,
			title = title,
			subtitle = subtitle,
			route = category.route,
			group = category.group,
			listed = category.listed,
			searchIndex = (listOf(title, subtitle) + category.keywords).joinToString(" ").lowercase()
		)
	}

/** Live one-line summaries shown under a few categories (current background, refresh rate...). */
@Composable
private fun rememberValueSummaries(prefs: OverlayPrefs): Map<String, String> {
	val context = LocalContext.current
	return remember(prefs, context) {
		val background =
			context.getString(
				when (prefs.background) {
					OverlayBackground.TRANSPARENT -> R.string.background_transparent
					OverlayBackground.BLACK -> R.string.background_black
					OverlayBackground.SEMI -> R.string.background_semi
				}
			)
		mapOf(
			Routes.APPEARANCE to context.getString(R.string.summary_background, background),
			Routes.GENERAL to context.getString(R.string.summary_refresh, prefs.updateIntervalSec),
			Routes.CLOCK to context.getString(R.string.summary_date_format, prefs.dateFormat)
		)
	}
}

/** Counts the blocked grants; a null value means the read has not finished yet. */
@Composable
private fun rememberBlockedGrantCount(shizukuReady: Boolean): Int? {
	val context = LocalContext.current
	// Bumped on every resume: grants are granted in other screens.
	var resumeTick by remember { mutableStateOf(0) }
	LifecycleResumeEffect(Unit) {
		resumeTick++
		onPauseOrDispose { }
	}
	val grants by produceState<PermissionGrants?>(null, resumeTick) {
		value = withContext(Dispatchers.IO) { PermissionAccess.grants(context) }
	}
	return grants?.let { loaded ->
		listOf(
			shizukuReady,
			loaded.overlay,
			loaded.notifListener,
			loaded.calendar,
			loaded.postNotifs,
			loaded.usage
		).count { !it }
	}
}

/**
 * Settings root: the category list, two groups - everything that shapes the
 * bar, then everything around the app. The search field lives in the shell's
 * top bar; matches keep their group, and sub-screens join the results even
 * though they are not listed here. The Debug row carries a count of the
 * missing grants so the board is discoverable without opening it.
 */
@Composable
fun SettingsRootScreen(
	vm: StatusViewModel,
	query: String,
	onNavigate: (String) -> Unit
) {
	val shizuku by vm.shizuku.collectAsStateWithLifecycle()
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	val trimmed = query.trim()
	// Resolved once per composition, not once per keystroke or per row.
	val resolved = rememberResolvedCategories()
	val blockedCount = rememberBlockedGrantCount(shizuku == ShizukuState.READY)
	val summaries = rememberValueSummaries(prefs)
	val matches =
		if (trimmed.isEmpty()) {
			resolved
		} else {
			resolved.filter { category -> category.searchIndex.contains(trimmed.lowercase()) }
		}
	val listed = matches.filter { it.listed }

	SettingsScreen {
		if (trimmed.isNotEmpty() && matches.isEmpty()) {
			item(key = "no-results") {
				Text(
					text = stringResource(R.string.settings_no_results),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(vertical = 16.dp)
				)
			}
		} else {
			categoryGroups(
				// Sub-screens can be the only match ("permissions", "keyguard
				// layout"): fall back to them when the listed rows are empty.
				rows = if (listed.isEmpty() && trimmed.isNotEmpty()) matches else listed,
				blockedCount = blockedCount,
				summaries = summaries,
				onNavigate = onNavigate
			)
		}
	}
}

/** The two category groups; [rows] is already filtered down to what the view shows. */
private fun LazyListScope.categoryGroups(
	rows: List<ResolvedCategory>,
	blockedCount: Int?,
	summaries: Map<String, String>,
	onNavigate: (String) -> Unit
) {
	listOf(R.string.section_custom_bar, R.string.section_app_group).forEach { group ->
		val groupRows = rows.filter { it.group == group }
		if (groupRows.isNotEmpty()) {
			// Keyed: a rotating query does not recompose the whole card.
			item(key = group) {
				SettingGroup(group) {
					groupRows.forEach { category ->
						val missing = category.route == Routes.DEBUG && blockedCount != null && blockedCount > 0
						val subtitle =
							when {
								missing -> stringResource(R.string.settings_missing_grants, blockedCount)
								else -> summaries[category.route] ?: category.subtitle
							}
						SettingNavRow(
							icon = category.icon,
							title = category.title,
							subtitle = subtitle,
							route = category.route,
							onNavigate = onNavigate,
							errorSubtitle = missing
						)
					}
				}
			}
		}
	}
}
