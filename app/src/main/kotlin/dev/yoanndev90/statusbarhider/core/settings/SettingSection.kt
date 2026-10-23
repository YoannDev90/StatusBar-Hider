package dev.yoanndev90.statusbarhider.core.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** Root listing bucket: which card of the settings home gathers the section. */
enum class SectionGroup { CUSTOM_BAR, APP }

/**
 * A settings screen declared as data: identity, listing metadata and the
 * rows to draw, in display order. The composable that draws a section lives
 * once in `ui/components`; migrating a screen means declaring it here and
 * deleting the hand-written file.
 *
 * Sections are static and context-free on purpose: the NavHost and the search
 * index hold them without a [android.content.Context]. Live state and writes
 * flow through the ViewModel the screen hands to the renderer — store access
 * per section (reset) plugs in there when it lands.
 */
@Suppress("LongParameterList") // Declarative descriptor like SettingSpec: identity and listing first, rows last.
abstract class SettingSection<S : Any>(
	/** Stable id; doubles as the route suffix ("connectivity" → settings/connectivity). */
	val id: String,
	@DrawableRes val icon: Int,
	@StringRes val title: Int,
	/** Subtitle of the root listing; 0 when the section has none (sub-screens). */
	@StringRes val subtitle: Int = 0,
	val group: SectionGroup,
	/** Extra search terms merged into the index built from the row labels. */
	val keywords: List<String>,
	/** Sub-screens stay searchable but are not listed on the settings home. */
	val listed: Boolean = true,
	val rows: List<SettingRow<S>>
) {
	/**
	 * [rows] minus the specs whose shownWhen is false for [prefs], groups
	 * emptied by the filter dropped too. The renderer draws exactly this, so
	 * what a test asserts here is what the user sees.
	 */
	fun visibleRows(prefs: S): List<SettingRow<S>> = rows.visible(prefs)
}

private fun <S : Any> List<SettingRow<S>>.visible(prefs: S): List<SettingRow<S>> =
	mapNotNull { row ->
		when (row) {
			is SettingRow.OfSpec -> row.takeIf { it.spec.shownWhen(prefs) }
			is SettingRow.Group ->
				SettingRow.Group(row.title, row.rows.visible(prefs)).takeIf { it.rows.isNotEmpty() }
			is SettingRow.Custom -> row
		}
	}
