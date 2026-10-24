package dev.yoanndev90.statusbarhider.core.settings.specs

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.BoolSpec
import dev.yoanndev90.statusbarhider.core.settings.EnumSpec
import dev.yoanndev90.statusbarhider.core.settings.LockScreenMode
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec

/** What the bar shows while the keyguard is up. */
object LockSpecs {
	val showOnLockScreen =
		BoolSpec<OverlayPrefs>(
			key = "show_on_lock_screen",
			get = OverlayPrefs::showOnLockScreen,
			set = { p, v -> p.copy(showOnLockScreen = v) },
			default = true,
			icon = R.drawable.ic_lock,
			label = R.string.switch_show_on_lock_screen
		)

	val lockScreenMode =
		EnumSpec<LockScreenMode, OverlayPrefs>(
			key = "lock_screen_mode",
			get = OverlayPrefs::lockScreenMode,
			set = { p, v -> p.copy(lockScreenMode = v) },
			default = LockScreenMode.FULL,
			entries =
				listOf(
					LockScreenMode.FULL to R.string.lock_mode_full,
					LockScreenMode.CLOCK_ONLY to R.string.lock_mode_clock_only
				),
			icon = R.drawable.ic_visibility
		)

	val hideNotifsOnLock =
		BoolSpec<OverlayPrefs>(
			key = "hide_notifs_on_lock",
			get = OverlayPrefs::hideNotifsOnLock,
			set = { p, v -> p.copy(hideNotifsOnLock = v) },
			default = false,
			icon = R.drawable.ic_notifications_off,
			label = R.string.switch_hide_notifs_on_lock
		)

	val specs: List<SettingSpec<*, OverlayPrefs>> = listOf(showOnLockScreen, lockScreenMode, hideNotifsOnLock)
}
