package dev.yoanndev90.statusbarhider.ui.screens.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.yoanndev90.statusbarhider.core.settings.EnumSpec
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.core.settings.SettingsRegistry
import dev.yoanndev90.statusbarhider.core.settings.Subtitle
import dev.yoanndev90.statusbarhider.core.settings.specs

/**
 * A section with its strings resolved once, search haystack included. The
 * haystack merges the row labels on top of the section's own words, so a
 * spec name ("24h", "transparent") reaches its section from the search field.
 */
internal data class ResolvedSection(
	val section: SettingSection<OverlayPrefs>,
	val title: String,
	/** Static subtitle when the section has one; null for a live or absent one. */
	val subtitle: String?,
	/** Lowercase haystack: title, subtitle, keywords and the row labels. */
	val searchIndex: String
)

/** Resolves every section title, subtitle and haystack once per composition. */
@Composable
internal fun rememberResolvedSections(): List<ResolvedSection> {
	val context = LocalContext.current
	return remember(context) { resolveSections(context) }
}

internal fun resolveSections(context: Context): List<ResolvedSection> =
	SettingsRegistry.sections.map { section ->
		val title = context.getString(section.title)
		val subtitle = (section.subtitle as? Subtitle.Res)?.id?.takeIf { it != 0 }?.let { context.getString(it) }
		ResolvedSection(
			section = section,
			title = title,
			subtitle = subtitle,
			searchIndex =
				(
					listOfNotNull(title, subtitle) +
						section.keywords +
						section.rows.specs().flatMap { spec -> spec.searchStrings(context) }
				).joinToString(" ")
					.lowercase()
		)
	}

/** A spec's words: its label, its subtitle and every enum entry label. */
private fun SettingSpec<*, OverlayPrefs>.searchStrings(context: Context): List<String> =
	listOfNotNull(
		label?.let { context.getString(it) },
		subtitle?.let { context.getString(it) }
	) + if (this is EnumSpec<*, *>) entries.map { context.getString(it.second) } else emptyList()

/**
 * What the root shows for [query]: listed matches, or unlisted ones when no
 * listed section matched ("permissions", "keyguard layout" reach sub-screens).
 */
internal fun searchSections(
	entries: List<ResolvedSection>,
	query: String
): List<ResolvedSection> {
	if (query.isEmpty()) return entries.filter { it.section.listed }
	val matches = entries.filter { it.searchIndex.contains(query.lowercase()) }
	return matches.filter { it.section.listed }.ifEmpty { matches }
}
