package dev.yoanndev90.statusbarhider.core.settings.sections

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingRow
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.Subtitle
import dev.yoanndev90.statusbarhider.core.settings.specs.LockSpecs

/**
 * The layout used on the lock screen: full bar or clock only. A sub-screen
 * of the lock hub — searchable, but not listed on the settings root.
 */
object LockAppearanceSection :
	SettingSection<OverlayPrefs>(
		id = "lock/appearance",
		icon = R.drawable.ic_palette,
		title = R.string.lock_sub_appearance,
		subtitle = Subtitle.Res(R.string.lock_sub_appearance_sub),
		group = SectionGroup.CUSTOM_BAR,
		keywords = listOf("lock", "keyguard", "layout", "full", "clock", "only"),
		listed = false,
		rows = listOf(SettingRow.OfSpec(LockSpecs.lockScreenMode))
	)
