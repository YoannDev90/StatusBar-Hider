package dev.yoanndev90.statusbarhider.core.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel

/**
 * One rendered line of a [SettingSection].
 *
 * The generic renderer (SpecRow in `ui/components`) draws [OfSpec] from the
 * spec's own metadata, [Group] opens a card of its own, and [Custom] is the
 * escape hatch for rendering no spec type describes — device pickers, drafts
 * with local state, permission launchers.
 */
sealed interface SettingRow<S : Any> {
	/** Drawn generically from [spec]; icon and label are mandatory (guarded by tests). */
	class OfSpec<S : Any>(
		val spec: SettingSpec<*, S>
	) : SettingRow<S>

	/**
	 * A card of its own. [title] 0 renders the untitled card, for blocks
	 * whose content is labelled inside rather than above.
	 */
	class Group<S : Any>(
		@StringRes val title: Int = 0,
		val rows: List<SettingRow<S>>
	) : SettingRow<S>

	/**
	 * Hand-written composable for what no spec type describes. Receives the
	 * screen's [PrefsViewModel] — the base every settings screen owns — so a
	 * custom row can log or run commands, not only write prefs.
	 */
	class Custom<S : Any>(
		val content: @Composable (S, PrefsViewModel) -> Unit
	) : SettingRow<S>
}

/** Rows for [specs] in order, each drawn by the generic renderer. */
fun <S : Any> settingRows(vararg specs: SettingSpec<*, S>): List<SettingRow<S>> = specs.map { SettingRow.OfSpec(it) }

/** Same, for lists already shaped as rows (mixed specs, groups and custom lines). */
fun <S : Any> settingRows(vararg rows: SettingRow<S>): List<SettingRow<S>> = rows.toList()
