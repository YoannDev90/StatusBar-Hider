package dev.yoanndev90.statusbarhider.core.settings.specs

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.BoolSpec
import dev.yoanndev90.statusbarhider.core.settings.IntSpec
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs

/** Notification widget: the icon row, its cap, and the now-playing line. */
object NotificationSpecs {
	val showNotifs =
		BoolSpec<OverlayPrefs>(
			key = "show_notifs",
			get = OverlayPrefs::showNotifs,
			set = { p, v -> p.copy(showNotifs = v) },
			default = true,
			icon = R.drawable.ic_notifications,
			label = R.string.switch_notification_icons
		)

	val maxNotifs =
		IntSpec<OverlayPrefs>(
			key = "max_notifs",
			get = OverlayPrefs::maxNotifs,
			set = { p, v -> p.copy(maxNotifs = v) },
			default = 5,
			min = 1,
			max = 8,
			icon = R.drawable.ic_filter_9_plus,
			label = R.string.slider_max_notification_icons
		)

	val showMedia =
		BoolSpec<OverlayPrefs>(
			key = "show_media",
			get = OverlayPrefs::showMedia,
			set = { p, v -> p.copy(showMedia = v) },
			default = false,
			icon = R.drawable.ic_media,
			label = R.string.switch_now_playing,
			subtitle = R.string.switch_now_playing_sub
		)

	val specs: List<SettingSpec<*, OverlayPrefs>> = listOf(showNotifs, maxNotifs, showMedia)
}
