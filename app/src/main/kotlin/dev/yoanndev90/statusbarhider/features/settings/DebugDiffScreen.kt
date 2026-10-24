package dev.yoanndev90.statusbarhider.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.PendingFocus
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.core.settings.SettingsRegistry
import dev.yoanndev90.statusbarhider.core.settings.specs
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/**
 * Debug > Modified settings: an index of the settings that are not at their
 * default, grouped by section. A line jumps to the setting's own screen —
 * [PendingFocus] scrolls it up to the card holding the setting, where the
 * green marker points it out; each group keeps one reset for the whole
 * section.
 */
@Composable
fun DebugDiffScreen(vm: StatusViewModel, onNavigate: (String) -> Unit) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	// One group per section that still differs from its defaults.
	val modified = remember(prefs) { modifiedSections(prefs) }

	SettingsScreen {
		if (modified.isEmpty()) {
			item {
				Text(
					text = stringResource(R.string.debug_diff_empty),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.fillMaxWidth().padding(24.dp),
					textAlign = TextAlign.Center
				)
			}
		}
		modified.forEach { (section, specs) ->
			item(key = "modified-${section.id}") {
				SettingGroup(section.title) {
					specs.forEach { spec ->
						IndexRow(
							// An enum spec may carry no row label of its own:
							// its values name it, so fall back to the key.
							label = spec.label?.let { stringResource(it) } ?: spec.key,
							value = spec.get(prefs).toString(),
							onClick = {
								PendingFocus.key = spec.key
								onNavigate(section.route)
							}
						)
					}
					SettingAction(R.drawable.ic_refresh, R.string.action_reset_settings) { vm.resetSection(section) }
				}
			}
		}
	}
}

/** One index entry: the setting's name, its current value, a chevron to its screen. */
@Composable
private fun IndexRow(label: String, value: String, onClick: () -> Unit) {
	Row(
		modifier =
			Modifier
				.fillMaxWidth()
				.clickable(onClick = onClick)
				.padding(vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f).padding(end = 8.dp)
		)
		Text(
			text = value,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		Icon(
			painter = painterResource(R.drawable.ic_chevron_right),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(20.dp).padding(start = 4.dp)
		)
	}
}

/** Sections with at least one setting away from its default, with those settings. */
internal fun modifiedSections(
	prefs: OverlayPrefs
): List<Pair<SettingSection<OverlayPrefs>, List<SettingSpec<*, OverlayPrefs>>>> =
	SettingsRegistry.sections.mapNotNull { section ->
		val modified = section.rows.specs().filter { it.isModifiedIn(prefs) }
		if (modified.isEmpty()) null else section to modified
	}
