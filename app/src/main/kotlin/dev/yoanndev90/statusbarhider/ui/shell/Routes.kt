package dev.yoanndev90.statusbarhider.ui.shell

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.SettingsRegistry

/**
 * Route table of the NavHost. Two levels deep at most: Settings lists the
 * categories, Lock screen and Debug are the only ones with a sub-level.
 */
object Routes {
	const val HOME = "home"
	const val SETTINGS = "settings"
	const val GENERAL = "settings/general"
	const val APPEARANCE = "settings/appearance"
	const val CUTOUT = "settings/cutout"
	const val CLOCK = "settings/clock"
	const val BATTERY = "settings/battery"
	const val NOTIFICATIONS = "settings/notifications"
	const val CONNECTIVITY = "settings/connectivity"
	const val HIDDEN_APPS = "settings/hidden_apps"
	const val LOCK = "settings/lock"
	const val LOCK_APPEARANCE = "settings/lock/appearance"
	const val LOCK_BEHAVIOR = "settings/lock/behavior"
	const val PROFILES = "settings/profiles"
	const val OEM = "settings/oem"
	const val DEBUG = "settings/debug"
	const val DEBUG_INFO = "settings/debug/info"
	const val DEBUG_PERMISSIONS = "settings/debug/permissions"
	const val LOGS = "settings/logs"
	const val BACKUP = "settings/backup"
	const val ABOUT = "settings/about"

	/**
	 * The shell's own titles; every settings screen is titled by its section
	 * in [SettingsRegistry].
	 */
	private val shellTitles: Map<String, Int> =
		mapOf(
			HOME to R.string.app_name,
			SETTINGS to R.string.settings_title
		)

	/** Top-bar title for a route; unknown routes fall back to the app name. */
	fun titleOf(route: String?): Int =
		SettingsRegistry.sectionByRoute(route)?.title
			?: shellTitles[route]
			?: R.string.app_name
}
