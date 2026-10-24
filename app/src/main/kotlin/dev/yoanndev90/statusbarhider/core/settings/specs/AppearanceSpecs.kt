package dev.yoanndev90.statusbarhider.core.settings.specs

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.BoolSpec
import dev.yoanndev90.statusbarhider.core.settings.EnumSpec
import dev.yoanndev90.statusbarhider.core.settings.IntSpec
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.overlay.BarFontWeight
import dev.yoanndev90.statusbarhider.overlay.OverlayBackground
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs

/** How the bar looks: colors, padding, text size and weight, spacing. */
object AppearanceSpecs {
	val darkText =
		BoolSpec<OverlayPrefs>(
			key = "dark_text",
			get = OverlayPrefs::darkText,
			set = { p, v -> p.copy(darkText = v) },
			default = false,
			icon = R.drawable.ic_contrast,
			label = R.string.switch_dark_text,
			subtitle = R.string.switch_dark_text_sub
		)

	val background =
		EnumSpec<OverlayBackground, OverlayPrefs>(
			key = "background",
			get = OverlayPrefs::background,
			set = { p, v -> p.copy(background = v) },
			default = OverlayBackground.SEMI,
			entries =
				listOf(
					OverlayBackground.TRANSPARENT to R.string.background_transparent,
					OverlayBackground.SEMI to R.string.background_semi,
					OverlayBackground.BLACK to R.string.background_black
				),
			icon = R.drawable.ic_palette
		)

	val padStartDp =
		IntSpec<OverlayPrefs>(
			key = "pad_start_dp",
			get = OverlayPrefs::padStartDp,
			set = { p, v -> p.copy(padStartDp = v) },
			default = 12,
			min = 0,
			max = 32,
			icon = R.drawable.ic_west,
			label = R.string.slider_padding_start,
			suffix = R.string.suffix_dp
		)

	val padTopDp =
		IntSpec<OverlayPrefs>(
			key = "pad_top_dp",
			get = OverlayPrefs::padTopDp,
			set = { p, v -> p.copy(padTopDp = v) },
			default = 4,
			min = 0,
			max = 32,
			icon = R.drawable.ic_north,
			label = R.string.slider_padding_top,
			suffix = R.string.suffix_dp
		)

	val padEndDp =
		IntSpec<OverlayPrefs>(
			key = "pad_end_dp",
			get = OverlayPrefs::padEndDp,
			set = { p, v -> p.copy(padEndDp = v) },
			default = 12,
			min = 0,
			max = 32,
			icon = R.drawable.ic_east,
			label = R.string.slider_padding_end,
			suffix = R.string.suffix_dp
		)

	val padBottomDp =
		IntSpec<OverlayPrefs>(
			key = "pad_bottom_dp",
			get = OverlayPrefs::padBottomDp,
			set = { p, v -> p.copy(padBottomDp = v) },
			default = 4,
			min = 0,
			max = 32,
			icon = R.drawable.ic_south,
			label = R.string.slider_padding_bottom,
			suffix = R.string.suffix_dp
		)

	val fontSizeSp =
		IntSpec<OverlayPrefs>(
			key = "font_size_sp",
			get = OverlayPrefs::fontSizeSp,
			set = { p, v -> p.copy(fontSizeSp = v) },
			default = 13,
			min = 10,
			max = 20,
			icon = R.drawable.ic_format_size,
			label = R.string.slider_text_size,
			suffix = R.string.suffix_sp
		)

	val fontWeightName =
		EnumSpec<BarFontWeight, OverlayPrefs>(
			key = "font_weight",
			get = OverlayPrefs::fontWeightName,
			set = { p, v -> p.copy(fontWeightName = v) },
			default = BarFontWeight.NORMAL,
			entries =
				listOf(
					BarFontWeight.NORMAL to R.string.font_weight_normal,
					BarFontWeight.MEDIUM to R.string.font_weight_medium,
					BarFontWeight.BOLD to R.string.font_weight_bold
				),
			icon = R.drawable.ic_format_bold,
			label = R.string.label_text_weight
		)

	val widgetSpacingDp =
		IntSpec<OverlayPrefs>(
			key = "widget_spacing_dp",
			get = OverlayPrefs::widgetSpacingDp,
			set = { p, v -> p.copy(widgetSpacingDp = v) },
			default = 6,
			min = 0,
			max = 12,
			icon = R.drawable.ic_space_bar,
			label = R.string.slider_widget_spacing,
			suffix = R.string.suffix_dp
		)

	val specs: List<SettingSpec<*, OverlayPrefs>> =
		listOf(darkText, background, padStartDp, padTopDp, padEndDp, padBottomDp, fontSizeSp, fontWeightName, widgetSpacingDp)
}
