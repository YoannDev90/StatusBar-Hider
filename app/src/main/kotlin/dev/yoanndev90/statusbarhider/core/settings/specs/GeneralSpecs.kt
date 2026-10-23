package dev.yoanndev90.statusbarhider.core.settings.specs

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.BoolSpec
import dev.yoanndev90.statusbarhider.core.settings.IntSpec
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs

/** Bar-wide behaviour: touch, boot handling, refresh cadence, burn-in shift. */
object GeneralSpecs {
	val interactive =
		BoolSpec<OverlayPrefs>(
			key = "interactive",
			get = OverlayPrefs::interactive,
			set = { p, v -> p.copy(interactive = v) },
			default = false,
			icon = R.drawable.ic_touch_app,
			label = R.string.switch_touchable_bar,
			subtitle = R.string.switch_touchable_bar_sub
		)

	val autoHideBoot =
		BoolSpec<OverlayPrefs>(
			key = "auto_hide_boot",
			get = OverlayPrefs::autoHideBoot,
			set = { p, v -> p.copy(autoHideBoot = v) },
			default = true,
			icon = R.drawable.ic_power_settings_new,
			label = R.string.switch_auto_hide_boot
		)

	val autoStartBar =
		BoolSpec<OverlayPrefs>(
			key = "auto_start_bar",
			get = OverlayPrefs::autoStartBar,
			set = { p, v -> p.copy(autoStartBar = v) },
			default = false,
			icon = R.drawable.ic_play_arrow,
			label = R.string.switch_auto_start_bar
		)

	val updateIntervalSec =
		IntSpec<OverlayPrefs>(
			key = "update_interval_sec",
			get = OverlayPrefs::updateIntervalSec,
			set = { p, v -> p.copy(updateIntervalSec = v) },
			default = 15,
			min = 5,
			max = 60,
			icon = R.drawable.ic_timer,
			label = R.string.slider_update_interval,
			suffix = R.string.suffix_seconds,
			subtitle = R.string.slider_update_interval_sub
		)

	val burnInMin =
		IntSpec<OverlayPrefs>(
			key = "burn_in_min",
			get = OverlayPrefs::burnInMin,
			set = { p, v -> p.copy(burnInMin = v) },
			default = 5,
			min = 0,
			max = 30,
			icon = R.drawable.ic_sync,
			label = R.string.slider_burn_in_shift,
			suffix = R.string.suffix_minutes,
			subtitle = R.string.slider_burn_in_shift_sub
		)

	val specs: List<SettingSpec<*, OverlayPrefs>> = listOf(interactive, autoHideBoot, autoStartBar, updateIntervalSec, burnInMin)
}
