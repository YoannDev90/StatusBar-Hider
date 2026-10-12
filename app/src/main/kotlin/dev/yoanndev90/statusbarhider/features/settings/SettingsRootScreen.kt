package dev.yoanndev90.statusbarhider.features.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingNavRow
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen
import dev.yoanndev90.statusbarhider.ui.shell.Routes

/** One row of the Settings list: where it points and how it reads. */
private data class Category(
	@DrawableRes val icon: Int,
	@StringRes val title: Int,
	@StringRes val subtitle: Int,
	val route: String,
	/** Group the row belongs to: bar settings first, app-level ones second. */
	val group: Int
)

private val categories =
	listOf(
		Category(
			R.drawable.ic_settings,
			R.string.cat_general,
			R.string.cat_general_sub,
			Routes.GENERAL,
			R.string.section_custom_bar
		),
		Category(
			R.drawable.ic_palette,
			R.string.cat_appearance,
			R.string.cat_appearance_sub,
			Routes.APPEARANCE,
			R.string.section_custom_bar
		),
		Category(
			R.drawable.ic_crop_free,
			R.string.cat_cutout,
			R.string.cat_cutout_sub,
			Routes.CUTOUT,
			R.string.section_custom_bar
		),
		Category(
			R.drawable.ic_schedule,
			R.string.cat_clock,
			R.string.cat_clock_sub,
			Routes.CLOCK,
			R.string.section_custom_bar
		),
		Category(
			R.drawable.ic_battery_std,
			R.string.cat_battery,
			R.string.cat_battery_sub,
			Routes.BATTERY,
			R.string.section_custom_bar
		),
		Category(
			R.drawable.ic_notifications,
			R.string.cat_notifications,
			R.string.cat_notifications_sub,
			Routes.NOTIFICATIONS,
			R.string.section_custom_bar
		),
		Category(
			R.drawable.ic_wifi,
			R.string.cat_connectivity,
			R.string.cat_connectivity_sub,
			Routes.CONNECTIVITY,
			R.string.section_custom_bar
		),
		Category(
			R.drawable.ic_apps,
			R.string.cat_hidden_apps,
			R.string.cat_hidden_apps_sub,
			Routes.HIDDEN_APPS,
			R.string.section_custom_bar
		),
		Category(
			R.drawable.ic_lock,
			R.string.cat_lock_screen,
			R.string.cat_lock_screen_sub,
			Routes.LOCK,
			R.string.section_custom_bar
		),
		Category(R.drawable.ic_person, R.string.cat_profiles, R.string.cat_profiles_sub, Routes.PROFILES, R.string.section_app_group),
		Category(R.drawable.ic_devices, R.string.cat_oem, R.string.cat_oem_sub, Routes.OEM, R.string.section_app_group),
		Category(R.drawable.ic_bug_report, R.string.cat_debug, R.string.cat_debug_sub, Routes.DEBUG, R.string.section_app_group),
		Category(R.drawable.ic_nav_log, R.string.cat_logs, R.string.cat_logs_sub, Routes.LOGS, R.string.section_app_group),
		Category(R.drawable.ic_save_alt, R.string.cat_backup, R.string.cat_backup_sub, Routes.BACKUP, R.string.section_app_group),
		Category(R.drawable.ic_info, R.string.cat_about, R.string.cat_about_sub, Routes.ABOUT, R.string.section_app_group)
	)

/**
 * Settings root: the category list, two groups - everything that shapes the
 * bar, then everything around the app. A search box at the top filters the
 * list live; while a query is active the groups collapse into one result set.
 */
@Composable
fun SettingsRootScreen(onNavigate: (String) -> Unit) {
	// saveable: the query survives a rotation and a round-trip down the stack.
	var query by rememberSaveable { mutableStateOf("") }
	val trimmed = query.trim()
	// Resolved once here: the LazyListScope content below is not composable.
	val matches =
		categories.filter { category ->
			trimmed.isEmpty() ||
				stringResource(category.title).contains(trimmed, ignoreCase = true) ||
				stringResource(category.subtitle).contains(trimmed, ignoreCase = true)
		}

	SettingsScreen {
		item {
			OutlinedTextField(
				value = query,
				onValueChange = { query = it },
				label = { Text(stringResource(R.string.settings_search)) },
				leadingIcon = {
					Icon(
						painter = painterResource(R.drawable.ic_search),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant
					)
				},
				trailingIcon = {
					if (query.isNotEmpty()) {
						IconButton(onClick = { query = "" }) {
							Icon(
								painter = painterResource(R.drawable.ic_close),
								contentDescription = stringResource(R.string.cd_clear),
								tint = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				},
				singleLine = true,
				modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
			)
		}

		if (trimmed.isNotEmpty()) {
			if (matches.isEmpty()) {
				item {
					Text(
						text = stringResource(R.string.settings_no_results),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(vertical = 16.dp)
					)
				}
			} else {
				item {
					SettingGroup {
						matches.forEach { category ->
							SettingNavRow(category.icon, category.title, category.subtitle, category.route, onNavigate)
						}
					}
				}
			}
		} else {
			listOf(R.string.section_custom_bar, R.string.section_app_group).forEach { group ->
				val rows = matches.filter { it.group == group }
				if (rows.isNotEmpty()) {
					item(key = group) {
						SettingGroup(group) {
							rows.forEach { category ->
								SettingNavRow(category.icon, category.title, category.subtitle, category.route, onNavigate)
							}
						}
					}
				}
			}
		}
	}
}
