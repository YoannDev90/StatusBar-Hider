package dev.yoanndev90.statusbarhider.core.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingRow
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.specs.CutoutSpecs
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.ColorSwatch
import dev.yoanndev90.statusbarhider.ui.components.ColorSwatchRow
import dev.yoanndev90.statusbarhider.ui.components.SettingLabelRow

/**
 * Camera / notch placement plus the optional progress ring. The screen's
 * if/else over auto-detect and over the ring switch is now declarative:
 * each slider's shownWhen decides, and the swatch row hides with the ring
 * from inside its own Custom row.
 */
object CutoutSection :
	SettingSection<OverlayPrefs>(
		id = "cutout",
		icon = R.drawable.ic_crop_free,
		title = R.string.cat_cutout,
		subtitle = R.string.cat_cutout_sub,
		group = SectionGroup.CUSTOM_BAR,
		keywords = listOf("notch", "camera", "ring", "color", "position"),
		rows =
			listOf(
				SettingRow.OfSpec(CutoutSpecs.cameraAutoDetect),
				SettingRow.OfSpec(CutoutSpecs.cameraNudgeXDp),
				SettingRow.OfSpec(CutoutSpecs.cameraNudgeYDp),
				SettingRow.OfSpec(CutoutSpecs.cameraOffsetXPct),
				SettingRow.OfSpec(CutoutSpecs.cameraOffsetYDp),
				SettingRow.OfSpec(CutoutSpecs.cameraSizeDp),
				SettingRow.OfSpec(CutoutSpecs.cameraGapDp),
				SettingRow.OfSpec(CutoutSpecs.cameraRing),
				SettingRow.OfSpec(CutoutSpecs.cameraRingStrokeDp),
				SettingRow.Custom { prefs, vm ->
					if (prefs.cameraRing) {
						SettingLabelRow(R.string.label_ring_color, R.drawable.ic_color_lens)
						ColorSwatchRow(
							swatches = ringSwatches(prefs),
							selected = prefs.cameraRingColor,
							onSelect = { vm.updatePrefs { copy(cameraRingColor = it) } }
						)
					}
				}
			)
	)

/** The auto entry follows the live text color; the rest are fixed hex chips. */
@Composable
private fun ringSwatches(prefs: OverlayPrefs): List<ColorSwatch> =
	listOf(
		ColorSwatch("", Color(prefs.textColor()), stringResource(R.string.color_auto)),
		ColorSwatch("#FFFFFF", Color.White, "#FFFFFF"),
		ColorSwatch("#FFCDD2", Color(0xFFFFCDD2), "#FFCDD2"),
		ColorSwatch("#FFE082", Color(0xFFFFE082), "#FFE082"),
		ColorSwatch("#A5D6A7", Color(0xFFA5D6A7), "#A5D6A7"),
		ColorSwatch("#80DEEA", Color(0xFF80DEEA), "#80DEEA"),
		ColorSwatch("#90CAF9", Color(0xFF90CAF9), "#90CAF9"),
		ColorSwatch("#CE93D8", Color(0xFFCE93D8), "#CE93D8")
	)
