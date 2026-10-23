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
	val cards = cardsOf(section.visibleRows(prefs))

	SettingsScreen {
		// Cards are immutable values computed before the lazy block: an item
		// lambda capturing a mutable runner would compose with its final,
		// emptied value — and draw an empty card.
		cards.forEach { card ->
			item {
				if (card.title != 0) {
					SettingGroup(card.title) { RenderRows(card.rows, prefs, vm, update) }
				} else {
					SettingGroup { RenderRows(card.rows, prefs, vm, update) }
				}
			}
		}
	}
}

/**
 * The section's rows grouped into cards, in display order: each loose run
 * becomes one untitled card, each [SettingRow.Group] keeps its own card and
 * title. Pure and eager — the lazy block only maps the result to items.
 */
internal fun cardsOf(rows: List<SettingRow<OverlayPrefs>>): List<SettingRow.Group<OverlayPrefs>> =
	buildList {
		var loose = emptyList<SettingRow<OverlayPrefs>>()
		rows.forEach { row ->
			when (row) {
				is SettingRow.Group -> {
					if (loose.isNotEmpty()) {
						add(SettingRow.Group(rows = loose))
						loose = emptyList()
					}
					add(row)
				}
				else -> loose = loose + row
			}
		}
		if (loose.isNotEmpty()) add(SettingRow.Group(rows = loose))
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
