package dev.yoanndev90.statusbarhider.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.core.settings.SettingRow
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs

/**
 * Hosts one overlay section: observes the live prefs, consumes the one-shot
 * events, and draws the section's visibleRows in declaration order — loose
 * rows under one untitled card (a new card restarts after each group), each
 * [SettingRow.Group] under its own card, title 0 keeping it untitled.
 *
 * Typed to [PrefsViewModel] because that is where overlay state and writes
 * live; a second state source can generalise it once AppSettings sections
 * exist.
 */
@Composable
fun SettingSectionScreen(vm: PrefsViewModel, section: SettingSection<OverlayPrefs>) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	HandlePrefsEvents(vm)
	val update: ((OverlayPrefs) -> OverlayPrefs) -> Unit = { transform -> vm.updatePrefs { transform(this) } }
	val visible = section.visibleRows(prefs)

	SettingsScreen {
		var loose = emptyList<SettingRow<OverlayPrefs>>()
		visible.forEach { row ->
			if (row is SettingRow.Group) {
				if (loose.isNotEmpty()) {
					item { SettingGroup { RenderRows(loose, prefs, vm, update) } }
					loose = emptyList()
				}
				item {
					if (row.title != 0) {
						SettingGroup(row.title) { RenderRows(row.rows, prefs, vm, update) }
					} else {
						SettingGroup { RenderRows(row.rows, prefs, vm, update) }
					}
				}
			} else {
				loose = loose + row
			}
		}
		if (loose.isNotEmpty()) item { SettingGroup { RenderRows(loose, prefs, vm, update) } }
	}
}

@Composable
private fun RenderRows(
	rows: List<SettingRow<OverlayPrefs>>,
	prefs: OverlayPrefs,
	vm: PrefsViewModel,
	update: ((OverlayPrefs) -> OverlayPrefs) -> Unit
) {
	rows.forEach { row ->
		when (row) {
			is SettingRow.OfSpec -> SpecRow(row.spec, prefs, update)
			is SettingRow.Custom -> row.content(prefs, vm)
			// A group nested inside a card flattens instead of a card in a card.
			is SettingRow.Group -> RenderRows(row.rows, prefs, vm, update)
		}
	}
}
