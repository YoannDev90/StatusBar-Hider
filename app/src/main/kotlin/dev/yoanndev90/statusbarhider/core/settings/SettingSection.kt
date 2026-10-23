package dev.yoanndev90.statusbarhider.core.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel

/** Root listing bucket: which card of the settings home gathers the section. */
enum class SectionGroup(
	@StringRes val titleRes: Int
) {
	CUSTOM_BAR(R.string.section_custom_bar),
	APP(R.string.section_app_group)
}

/** A section subtitle: a fixed string, or one computed from the live settings. */
sealed interface Subtitle<S : Any> {
	/** Fixed string; the default id 0 means "no subtitle". */
	class Res<S : Any>(
		@StringRes val id: Int = 0
	) : Subtitle<S>

	/** Live summary computed from the settings — only a [Res] feeds search. */
	class Live<S : Any>(
		val provide: @Composable (S) -> String
	) : Subtitle<S>
}

/**
 * A settings screen declared as data: identity, listing metadata and the
 * rows to draw, in display order. The section is the single source of the
 * navigation line, the top-bar title and the search entry; the composable
 * that draws its rows lives once in `ui/components`.
 *
 * Sections are static and context-free on purpose: the NavHost and the search
 * index hold them without a [android.content.Context]. Live state and writes
 * flow through the ViewModel the screen hands to the renderer — store access
 * per section (reset) plugs in there when it lands. Sections with no rows
 * describe screens that are still hand-written (the app group, the lock hub).
 */
@Suppress("LongParameterList") // Declarative descriptor like SettingSpec: identity and listing first, rows last.
abstract class SettingSection<S : Any>(
	/** Stable id; doubles as the route suffix ("lock/appearance" → settings/lock/appearance). */
	val id: String,
	@DrawableRes val icon: Int,
	@StringRes val title: Int,
	/** Subtitle of the root listing; [Subtitle.Res] id 0 when the section has none. */
	val subtitle: Subtitle<S> = Subtitle.Res(),
	val group: SectionGroup,
	/** Extra search terms merged into the index built from the row labels. */
	val keywords: List<String>,
	/** Sub-screens stay searchable but are not listed on the settings home. */
	val listed: Boolean = true,
	/** Live text shown in place of the subtitle (the Debug grants badge). */
	val badge: @Composable (StatusViewModel) -> String? = { null },
	/** The rows this section draws; empty when its screen is still hand-written. */
	val rows: List<SettingRow<S>> = emptyList()
) {
	/** Route of the screen this section describes. */
	val route: String get() = "settings/$id"

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
