package dev.yoanndev90.statusbarhider.core.settings.specs

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.BoolSpec
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec

/** Battery widget: the cell itself, its percentage and its icon. */
object BatterySpecs {
	val showBattery =
		BoolSpec<OverlayPrefs>(
			key = "show_battery",
			get = OverlayPrefs::showBattery,
			set = { p, v -> p.copy(showBattery = v) },
			default = true,
			icon = R.drawable.ic_battery_std,
			label = R.string.switch_battery
		)

	val showBatteryPct =
		BoolSpec<OverlayPrefs>(
			key = "show_battery_pct",
			get = OverlayPrefs::showBatteryPct,
			set = { p, v -> p.copy(showBatteryPct = v) },
			default = true,
			icon = R.drawable.ic_percent,
			label = R.string.switch_battery_pct
		)

	val showBatteryIcon =
		BoolSpec<OverlayPrefs>(
			key = "show_battery_icon",
			get = OverlayPrefs::showBatteryIcon,
			set = { p, v -> p.copy(showBatteryIcon = v) },
			default = true,
			icon = R.drawable.ic_battery_full,
			label = R.string.switch_battery_icon
		)

	val specs: List<SettingSpec<*, OverlayPrefs>> = listOf(showBattery, showBatteryPct, showBatteryIcon)
}
