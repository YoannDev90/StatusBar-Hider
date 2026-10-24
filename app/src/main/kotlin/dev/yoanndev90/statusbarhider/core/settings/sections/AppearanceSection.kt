package dev.yoanndev90.statusbarhider.core.settings.sections

import androidx.compose.ui.res.stringResource
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayBackground
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingRow
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.Subtitle
import dev.yoanndev90.statusbarhider.core.settings.specs.AppearanceSpecs
import dev.yoanndev90.statusbarhider.features.settings.WidgetOrderGroup

/** Background, paddings, type, and the drag-to-reorder widget list. */
object AppearanceSection :
	SettingSection<OverlayPrefs>(
		id = "appearance",
		icon = R.drawable.ic_palette,
		title = R.string.cat_appearance,
		subtitle =
			Subtitle.Live { prefs ->
				stringResource(
					R.string.summary_background,
					stringResource(
						when (prefs.background) {
							OverlayBackground.TRANSPARENT -> R.string.background_transparent
							OverlayBackground.BLACK -> R.string.background_black
							OverlayBackground.SEMI -> R.string.background_semi
						}
					)
				)
			},
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
