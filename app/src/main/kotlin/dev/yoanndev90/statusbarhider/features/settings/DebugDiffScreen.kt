package dev.yoanndev90.statusbarhider.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.core.settings.SettingsRegistry
import dev.yoanndev90.statusbarhider.core.settings.specs
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** The "+added" green of a diff, readable on both card tones. */
private val DiffAdded = Color(0xFF2E7D32)

/**
 * Debug > Modified settings: the settings that are not at their default,
 * grouped by section, each on one diff-style line — the current value as
 * `+added` in green over the default as `-removed` in red — with a one-tap
 * reset for the setting or the whole section.
 */
@Composable
fun DebugDiffScreen(vm: StatusViewModel) {
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
						DiffRow(
							// An enum spec may carry no row label of its own:
							// its values name it, so fall back to the key.
							label = spec.label?.let { stringResource(it) } ?: spec.key,
							current = spec.get(prefs).toString(),
							default = spec.default.toString(),
							onReset = { vm.resetSpec(spec) }
						)
					}
					SettingAction(R.drawable.ic_refresh, R.string.action_reset_settings) { vm.resetSection(section) }
				}
			}
		}
	}
}

/** One modified setting: name, `+current` over `-default`, and an icon-only reset. */
@Composable
private fun DiffRow(
	label: String,
	current: String,
	default: String,
	onReset: () -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.weight(1.2f).padding(end = 8.dp)
		)
		Column(Modifier.weight(1f)) {
			Text(
				text = "+$current",
				style = MaterialTheme.typography.bodySmall,
				fontFamily = FontFamily.Monospace,
				color = DiffAdded
			)
			Text(
				text = "-$default",
				style = MaterialTheme.typography.bodySmall,
				fontFamily = FontFamily.Monospace,
				color = MaterialTheme.colorScheme.error
			)
		}
		IconButton(onClick = onReset) {
			Icon(
				painter = painterResource(R.drawable.ic_refresh),
				contentDescription = stringResource(R.string.action_reset),
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(20.dp)
			)
		}
	}
}

/** Sections with at least one setting away from its default, with those settings. */
private fun modifiedSections(
	prefs: OverlayPrefs
): List<Pair<SettingSection<OverlayPrefs>, List<SettingSpec<*, OverlayPrefs>>>> =
	SettingsRegistry.sections.mapNotNull { section ->
		val modified = section.rows.specs().filter { it.isModifiedIn(prefs) }
		if (modified.isEmpty()) null else section to modified
	}
