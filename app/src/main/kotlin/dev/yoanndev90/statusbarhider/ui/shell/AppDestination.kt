package dev.yoanndev90.statusbarhider.ui.shell

import androidx.annotation.DrawableRes
import dev.yoanndev90.statusbarhider.R

/**
 * Top-level destinations of the bottom navigation bar. Each entry is one tab:
 * [iconRes] / [labelRes] feed the NavigationBarItem and [titleRes] the top bar.
 */
enum class AppDestination(
	@DrawableRes val iconRes: Int,
	val labelRes: Int,
	val titleRes: Int
) {
	STATUS(R.drawable.ic_tile_hide, R.string.nav_status, R.string.app_name),
	BAR(R.drawable.ic_tile_bar, R.string.nav_bar, R.string.nav_bar),
	STYLE(R.drawable.ic_nav_style, R.string.nav_style, R.string.nav_style),
	LOG(R.drawable.ic_nav_log, R.string.nav_log, R.string.nav_log);

	companion object {
		/** First tab, and the one the back press pops to. */
		val START = STATUS
	}
}
