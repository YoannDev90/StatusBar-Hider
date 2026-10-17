package dev.yoanndev90.statusbarhider.ui.shell

import dev.yoanndev90.statusbarhider.R

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

	private val titles: Map<String, Int> =
		mapOf(
			HOME to R.string.app_name,
			SETTINGS to R.string.settings_title,
			GENERAL to R.string.cat_general,
			APPEARANCE to R.string.cat_appearance,
			CUTOUT to R.string.cat_cutout,
			CLOCK to R.string.cat_clock,
			BATTERY to R.string.cat_battery,
			NOTIFICATIONS to R.string.cat_notifications,
			CONNECTIVITY to R.string.cat_connectivity,
			HIDDEN_APPS to R.string.cat_hidden_apps,
			LOCK to R.string.cat_lock_screen,
			LOCK_APPEARANCE to R.string.lock_sub_appearance,
			LOCK_BEHAVIOR to R.string.lock_sub_behavior,
			PROFILES to R.string.cat_profiles,
			OEM to R.string.cat_oem,
			DEBUG to R.string.cat_debug,
			DEBUG_INFO to R.string.debug_sub_info,
			DEBUG_PERMISSIONS to R.string.debug_sub_permissions,
			LOGS to R.string.cat_logs,
			BACKUP to R.string.cat_backup,
			ABOUT to R.string.cat_about
		)

	/** Top-bar title for a route; unknown routes fall back to the app name. */
	fun titleOf(route: String?): Int = titles[route] ?: R.string.app_name
}
