package dev.yoanndev90.statusbarhider.features.style.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayBackground
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingRadioRow
import dev.yoanndev90.statusbarhider.ui.components.SettingSlider
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch

/** Stored ids behind the weight choices; indexes map to [OverlayPrefs.fontWeightName]. */
private val FONT_WEIGHT_IDS = listOf("NORMAL", "MEDIUM", "BOLD")

/** Background, paddings, type and the show / hide switch of the custom bar. */
@Composable
fun LookSection(
	prefs: OverlayPrefs,
	vm: PrefsViewModel
) {
	SettingGroup(R.string.section_custom_bar) {
		SettingAction(R.drawable.ic_visibility, R.string.action_show_custom_bar) { vm.showOverlayBar() }
		SettingAction(R.drawable.ic_visibility_off, R.string.action_hide_custom_bar) { vm.setOverlayEnabled(false) }

		SettingSwitch(R.drawable.ic_contrast, R.string.switch_dark_text, prefs.darkText) { vm.updatePrefs { copy(darkText = it) } }
		SettingRadioRow(
			icon = R.drawable.ic_palette,
			options =
				listOf(
					stringResource(R.string.background_transparent),
					stringResource(R.string.background_semi),
					stringResource(R.string.background_black)
				),
			selected = backgroundIndex(prefs.background),
			onSelect = { index ->
				val bg =
					when (index) {
						0 -> OverlayBackground.TRANSPARENT
						2 -> OverlayBackground.BLACK
						else -> OverlayBackground.SEMI
					}
				vm.updatePrefs { copy(background = bg) }
			}
		)

		SettingSlider(R.drawable.ic_west, R.string.slider_padding_start, prefs.padStartDp, 0, 32, R.string.suffix_dp) {
			vm.updatePrefs { copy(padStartDp = it) }
		}
		SettingSlider(R.drawable.ic_north, R.string.slider_padding_top, prefs.padTopDp, 0, 32, R.string.suffix_dp) {
			vm.updatePrefs { copy(padTopDp = it) }
		}
		SettingSlider(R.drawable.ic_east, R.string.slider_padding_end, prefs.padEndDp, 0, 32, R.string.suffix_dp) {
			vm.updatePrefs { copy(padEndDp = it) }
		}
		SettingSlider(R.drawable.ic_south, R.string.slider_padding_bottom, prefs.padBottomDp, 0, 32, R.string.suffix_dp) {
			vm.updatePrefs { copy(padBottomDp = it) }
		}
		SettingSlider(R.drawable.ic_format_size, R.string.slider_text_size, prefs.fontSizeSp, 10, 20, R.string.suffix_sp) {
			vm.updatePrefs { copy(fontSizeSp = it) }
		}
		SettingSlider(R.drawable.ic_space_bar, R.string.slider_widget_spacing, prefs.widgetSpacingDp, 0, 12, R.string.suffix_dp) {
			vm.updatePrefs { copy(widgetSpacingDp = it) }
		}

		Text(
			text = stringResource(R.string.label_text_weight),
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(top = 8.dp)
		)
		SettingRadioRow(
			icon = R.drawable.ic_format_bold,
			options =
				listOf(
					stringResource(R.string.font_weight_normal),
					stringResource(R.string.font_weight_medium),
					stringResource(R.string.font_weight_bold)
				),
			selected = FONT_WEIGHT_IDS.indexOf(prefs.fontWeightName).coerceAtLeast(0),
			onSelect = { vm.updatePrefs { copy(fontWeightName = FONT_WEIGHT_IDS[it]) } }
		)
	}
}

private fun backgroundIndex(background: OverlayBackground): Int =
	when (background) {
		OverlayBackground.TRANSPARENT -> 0
		OverlayBackground.BLACK -> 2
		OverlayBackground.SEMI -> 1
	}
