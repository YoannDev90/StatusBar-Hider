package dev.yoanndev90.statusbarhider.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.Subtitle
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingNavRow
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen
import dev.yoanndev90.statusbarhider.ui.screens.status.StatusViewModel

/**
 * Settings root: the category list built from [SettingsRegistry.sections],
 * two groups - everything that shapes the bar, then everything around the
 * app. The search field lives in the shell's top bar; matches keep their
 * group, and sub-screens join the results even though they are not listed
 * here. A section's badge (the Debug grants count) replaces its subtitle
 * when present.
 */
@Composable
fun SettingsRootScreen(
	vm: StatusViewModel,
	query: String,
	onNavigate: (String) -> Unit
) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	val trimmed = query.trim()
	// Resolved once per composition, not once per keystroke or per row.
	val resolved = rememberResolvedSections()
	val shown = searchSections(resolved, trimmed)

	SettingsScreen {
		if (trimmed.isNotEmpty() && shown.isEmpty()) {
			item(key = "no-results") {
				Text(
					text = stringResource(R.string.settings_no_results),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(vertical = 16.dp)
				)
			}
		} else {
			sectionGroups(
				rows = shown,
				prefs = prefs,
				vm = vm,
				onNavigate = onNavigate
			)
		}
	}
}

/** The two category groups; [rows] is already filtered down to what the view shows. */
private fun LazyListScope.sectionGroups(
	rows: List<ResolvedSection>,
	prefs: OverlayPrefs,
	vm: StatusViewModel,
	onNavigate: (String) -> Unit
) {
	SectionGroup.entries.forEach { group ->
		val groupRows = rows.filter { it.section.group == group }
		if (groupRows.isNotEmpty()) {
			// Keyed: a rotating query does not recompose the whole card.
			item(key = group.titleRes) {
				SettingGroup(group.titleRes) {
					groupRows.forEach { row ->
						val section = row.section
						val badge = section.badge(vm)
						val subtitle =
							badge
								?: when (val live = section.subtitle) {
									is Subtitle.Live -> live.provide(prefs)
									is Subtitle.Res -> row.subtitle.orEmpty()
								}
						SettingNavRow(
							icon = section.icon,
							title = row.title,
							subtitle = subtitle,
							route = section.route,
							onNavigate = onNavigate,
							errorSubtitle = badge != null
						)
					}
				}
			}
		}
	}
}
