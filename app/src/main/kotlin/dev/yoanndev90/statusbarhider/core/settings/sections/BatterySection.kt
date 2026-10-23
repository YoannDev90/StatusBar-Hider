package dev.yoanndev90.statusbarhider.core.settings.sections

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.settingRows
import dev.yoanndev90.statusbarhider.core.settings.specs.BatterySpecs
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs

/** Battery level, percentage and icon. */
object BatterySection :
	SettingSection<OverlayPrefs>(
		id = "battery",
		icon = R.drawable.ic_battery_std,
		title = R.string.cat_battery,
		subtitle = R.string.cat_battery_sub,
		group = SectionGroup.CUSTOM_BAR,
		keywords = listOf("level", "percent", "icon"),
		rows =
			settingRows(
				BatterySpecs.showBattery,
				BatterySpecs.showBatteryPct,
				BatterySpecs.showBatteryIcon
			)
	)
