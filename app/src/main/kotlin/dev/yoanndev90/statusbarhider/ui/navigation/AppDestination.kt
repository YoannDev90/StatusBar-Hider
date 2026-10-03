package dev.yoanndev90.statusbarhider.ui.navigation

import androidx.annotation.DrawableRes
import dev.yoanndev90.statusbarhider.R

/**
 * Top-level destinations of the bottom navigation bar. Each entry is one tab:
 * [route] is the NavHost graph route, [iconRes] / [labelRes] feed the
 * NavigationBarItem and [titleRes] the top bar.
 */
enum class AppDestination(
	val route: String,
	@DrawableRes val iconRes: Int,
	val labelRes: Int,
	val titleRes: Int
) {
	STATUS("status", R.drawable.ic_tile_hide, R.string.nav_status, R.string.app_name),
	BAR("bar", R.drawable.ic_tile_bar, R.string.nav_bar, R.string.nav_bar),
	STYLE("style", R.drawable.ic_nav_style, R.string.nav_style, R.string.nav_style),
	LOG("log", R.drawable.ic_nav_log, R.string.nav_log, R.string.nav_log);

	companion object {
		/** First tab, and the one back navigations pop up to. */
		val START = STATUS

		/** Resolves a NavHost route; unknown / not-yet-composed routes fall back to [START]. */
		fun fromRoute(route: String?): AppDestination = entries.firstOrNull { it.route == route } ?: START
	}
}
