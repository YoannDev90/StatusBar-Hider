package dev.yoanndev90.statusbarhider.features.style.sections

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.ColorSwatch
import dev.yoanndev90.statusbarhider.ui.components.ColorSwatchRow
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSlider
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch

/** Camera / notch placement, plus the optional progress ring around it. */
@Composable
fun CutoutSection(
	prefs: OverlayPrefs,
	vm: PrefsViewModel
) {
	SettingGroup(R.string.section_cutout) {
		SettingSwitch(R.drawable.ic_crop_free, R.string.switch_camera_auto, prefs.cameraAutoDetect) {
			vm.updatePrefs { copy(cameraAutoDetect = it) }
		}
		if (prefs.cameraAutoDetect) {
			SettingSlider(R.drawable.ic_drag_indicator, R.string.slider_camera_nudge_x, prefs.cameraNudgeXDp, -48, 48, R.string.suffix_dp) {
				vm.updatePrefs { copy(cameraNudgeXDp = it) }
			}
			SettingSlider(R.drawable.ic_drag_indicator, R.string.slider_camera_nudge_y, prefs.cameraNudgeYDp, -48, 48, R.string.suffix_dp) {
				vm.updatePrefs { copy(cameraNudgeYDp = it) }
			}
		} else {
			SettingSlider(R.drawable.ic_open_with, R.string.slider_camera_offset_x, prefs.cameraOffsetXPct, 0, 100, R.string.suffix_percent) {
				vm.updatePrefs { copy(cameraOffsetXPct = it) }
			}
			SettingSlider(R.drawable.ic_open_with, R.string.slider_camera_offset_y, prefs.cameraOffsetYDp, 0, 60, R.string.suffix_dp) {
				vm.updatePrefs { copy(cameraOffsetYDp = it) }
			}
		}
		SettingSlider(R.drawable.ic_select_all, R.string.slider_camera_size, prefs.cameraSizeDp, 0, 40, R.string.suffix_dp) {
			vm.updatePrefs { copy(cameraSizeDp = it) }
		}
		SettingSlider(R.drawable.ic_straighten, R.string.slider_camera_gap, prefs.cameraGapDp, 0, 16, R.string.suffix_dp) {
			vm.updatePrefs { copy(cameraGapDp = it) }
		}
		SettingSwitch(R.drawable.ic_donut_small, R.string.switch_camera_ring, prefs.cameraRing) { vm.updatePrefs { copy(cameraRing = it) } }
		if (prefs.cameraRing) {
			SettingSlider(R.drawable.ic_line_weight, R.string.slider_camera_ring_stroke, prefs.cameraRingStrokeDp, 1, 8, R.string.suffix_dp) {
				vm.updatePrefs { copy(cameraRingStrokeDp = it) }
			}
			Row(
				modifier = Modifier.padding(top = 8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(
					painter = painterResource(R.drawable.ic_color_lens),
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(20.dp)
				)
				Spacer(Modifier.width(12.dp))
				Text(
					text = stringResource(R.string.label_ring_color),
					style = MaterialTheme.typography.bodyMedium
				)
			}
			ColorSwatchRow(
				swatches = ringSwatches(prefs),
				selected = prefs.cameraRingColor,
				onSelect = { vm.updatePrefs { copy(cameraRingColor = it) } }
			)
		}
	}
}

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
