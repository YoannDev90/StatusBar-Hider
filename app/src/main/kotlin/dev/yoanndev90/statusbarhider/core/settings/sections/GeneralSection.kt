package dev.yoanndev90.statusbarhider.core.settings.sections

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.settingRows
import dev.yoanndev90.statusbarhider.core.settings.specs.GeneralSpecs
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs

/** Polling, touch handling, boot behavior and burn-in protection. */
object GeneralSection :
	SettingSection<OverlayPrefs>(
		id = "general",
		icon = R.drawable.ic_settings,
		title = R.string.cat_general,
		subtitle = R.string.cat_general_sub,
		group = SectionGroup.CUSTOM_BAR,
		keywords = listOf("refresh", "boot", "touch", "polling", "burn-in", "interval", "auto-hide"),
		rows =
			settingRows(
				GeneralSpecs.interactive,
				GeneralSpecs.autoHideBoot,
				GeneralSpecs.autoStartBar,
				GeneralSpecs.updateIntervalSec,
				GeneralSpecs.burnInMin
			)
	)
