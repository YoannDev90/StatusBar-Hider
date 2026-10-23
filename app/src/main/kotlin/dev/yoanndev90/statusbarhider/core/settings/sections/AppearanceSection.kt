package dev.yoanndev90.statusbarhider.core.settings.sections

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingRow
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.specs.AppearanceSpecs
import dev.yoanndev90.statusbarhider.features.settings.WidgetOrderGroup
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs

/** Background, paddings, type, and the drag-to-reorder widget list. */
object AppearanceSection :
	SettingSection<OverlayPrefs>(
		id = "appearance",
		icon = R.drawable.ic_palette,
		title = R.string.cat_appearance,
		subtitle = R.string.cat_appearance_sub,
		group = SectionGroup.CUSTOM_BAR,
		keywords = listOf("background", "transparent", "semi", "black", "text", "size", "padding", "weight", "widget", "order"),
		rows =
			listOf(
				SettingRow.OfSpec(AppearanceSpecs.darkText),
				SettingRow.OfSpec(AppearanceSpecs.background),
				SettingRow.OfSpec(AppearanceSpecs.padStartDp),
				SettingRow.OfSpec(AppearanceSpecs.padTopDp),
				SettingRow.OfSpec(AppearanceSpecs.padEndDp),
				SettingRow.OfSpec(AppearanceSpecs.padBottomDp),
				SettingRow.OfSpec(AppearanceSpecs.fontSizeSp),
				SettingRow.OfSpec(AppearanceSpecs.widgetSpacingDp),
				SettingRow.OfSpec(AppearanceSpecs.fontWeightName),
				SettingRow.Group(
					title = R.string.section_widget_order,
					rows =
						listOf(
							SettingRow.Custom { prefs, vm -> WidgetOrderGroup(prefs.widgetOrder, vm::moveWidget) }
						)
				)
			)
	)
