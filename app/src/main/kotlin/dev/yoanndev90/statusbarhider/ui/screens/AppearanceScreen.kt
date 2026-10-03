package dev.yoanndev90.statusbarhider.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.overlay.OverlayBackground
import dev.yoanndev90.statusbarhider.overlay.WidgetId
import dev.yoanndev90.statusbarhider.ui.MainViewModel
import dev.yoanndev90.statusbarhider.ui.components.ColorSwatch
import dev.yoanndev90.statusbarhider.ui.components.ColorSwatchRow
import dev.yoanndev90.statusbarhider.ui.components.SectionHeader
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingRadioRow
import dev.yoanndev90.statusbarhider.ui.components.SettingSlider
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

/** Style page: widget order, then the look of the custom bar (background, paddings, type). */
@Composable
fun AppearanceScreen(
	vm: MainViewModel,
	onShowBar: () -> Unit
) {
	val state by vm.uiState.collectAsStateWithLifecycle()
	val prefs = state.prefs
	val haptic = LocalHapticFeedback.current

	Column(
		modifier =
			Modifier
				.fillMaxSize()
				.padding(horizontal = 24.dp)
				.verticalScroll(rememberScrollState())
	) {
		SectionHeader(stringResource(R.string.section_widget_order))
		Text(
			text = stringResource(R.string.label_widget_order_hint),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(bottom = 4.dp)
		)
		ReorderableColumn(
			list = prefs.widgetOrder,
			onSettle = { fromIndex, toIndex ->
				vm.updatePrefs {
					copy(
						widgetOrder =
							widgetOrder.toMutableList().apply {
								add(toIndex, removeAt(fromIndex))
							}
					)
				}
			},
			onMove = {
				haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
			},
			verticalArrangement = Arrangement.spacedBy(2.dp)
		) { _, id, isDragging ->
			key(id) {
				ReorderableItem {
					val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
					Surface(shadowElevation = elevation) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(
								text = stringResource(WidgetId.labelRes(id)),
								style = MaterialTheme.typography.bodyMedium,
								modifier = Modifier.weight(1f).padding(vertical = 12.dp)
							)
							IconButton(
								modifier =
									Modifier.draggableHandle(
										onDragStarted = {
											haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
										},
										onDragStopped = {
											haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
										}
									),
								onClick = {}
							) {
								Icon(
									imageVector = Icons.Filled.Menu,
									contentDescription = stringResource(R.string.cd_reorder)
								)
							}
						}
					}
				}
			}
		}

		SettingSwitch(stringResource(R.string.switch_dark_text), prefs.darkText) {
			vm.updatePrefs { copy(darkText = it) }
		}
		SettingRadioRow(
			options =
				listOf(
					stringResource(R.string.background_transparent),
					stringResource(R.string.background_semi),
					stringResource(R.string.background_black)
				),
			selected =
				when (prefs.background) {
					OverlayBackground.TRANSPARENT -> 0
					OverlayBackground.BLACK -> 2
					else -> 1
				},
			onSelect = {
				val bg =
					when (it) {
						0 -> OverlayBackground.TRANSPARENT
						2 -> OverlayBackground.BLACK
						else -> OverlayBackground.SEMI
					}
				vm.updatePrefs { copy(background = bg) }
			}
		)
		SettingSlider(stringResource(R.string.slider_padding_start), prefs.padStartDp, 0, 32, stringResource(R.string.suffix_dp)) {
			vm.updatePrefs { copy(padStartDp = it) }
		}
		SettingSlider(stringResource(R.string.slider_padding_top), prefs.padTopDp, 0, 32, stringResource(R.string.suffix_dp)) {
			vm.updatePrefs { copy(padTopDp = it) }
		}
		SettingSlider(stringResource(R.string.slider_padding_end), prefs.padEndDp, 0, 32, stringResource(R.string.suffix_dp)) {
			vm.updatePrefs { copy(padEndDp = it) }
		}
		SettingSlider(stringResource(R.string.slider_padding_bottom), prefs.padBottomDp, 0, 32, stringResource(R.string.suffix_dp)) {
			vm.updatePrefs { copy(padBottomDp = it) }
		}
		SettingSlider(stringResource(R.string.slider_text_size), prefs.fontSizeSp, 10, 20, stringResource(R.string.suffix_sp)) {
			vm.updatePrefs { copy(fontSizeSp = it) }
		}
		SettingSlider(stringResource(R.string.slider_widget_spacing), prefs.widgetSpacingDp, 0, 12, stringResource(R.string.suffix_dp)) {
			vm.updatePrefs { copy(widgetSpacingDp = it) }
		}

		SectionHeader(stringResource(R.string.section_cutout))
		SettingSwitch(stringResource(R.string.switch_camera_auto), prefs.cameraAutoDetect) {
			vm.updatePrefs { copy(cameraAutoDetect = it) }
		}
		if (prefs.cameraAutoDetect) {
			SettingSlider(stringResource(R.string.slider_camera_nudge_x), prefs.cameraNudgeXDp, -48, 48, stringResource(R.string.suffix_dp)) {
				vm.updatePrefs { copy(cameraNudgeXDp = it) }
			}
			SettingSlider(stringResource(R.string.slider_camera_nudge_y), prefs.cameraNudgeYDp, -48, 48, stringResource(R.string.suffix_dp)) {
				vm.updatePrefs { copy(cameraNudgeYDp = it) }
			}
		} else {
			SettingSlider(stringResource(R.string.slider_camera_offset_x), prefs.cameraOffsetXPct, 0, 100, stringResource(R.string.suffix_percent)) {
				vm.updatePrefs { copy(cameraOffsetXPct = it) }
			}
			SettingSlider(stringResource(R.string.slider_camera_offset_y), prefs.cameraOffsetYDp, 0, 60, stringResource(R.string.suffix_dp)) {
				vm.updatePrefs { copy(cameraOffsetYDp = it) }
			}
		}
		SettingSlider(stringResource(R.string.slider_camera_size), prefs.cameraSizeDp, 0, 40, stringResource(R.string.suffix_dp)) {
			vm.updatePrefs { copy(cameraSizeDp = it) }
		}
		SettingSlider(stringResource(R.string.slider_camera_gap), prefs.cameraGapDp, 0, 16, stringResource(R.string.suffix_dp)) {
			vm.updatePrefs { copy(cameraGapDp = it) }
		}
		SettingSwitch(stringResource(R.string.switch_camera_ring), prefs.cameraRing) {
			vm.updatePrefs { copy(cameraRing = it) }
		}
		if (prefs.cameraRing) {
			SettingSlider(stringResource(R.string.slider_camera_ring_stroke), prefs.cameraRingStrokeDp, 1, 8, stringResource(R.string.suffix_dp)) {
				vm.updatePrefs { copy(cameraRingStrokeDp = it) }
			}
			Text(
				text = stringResource(R.string.label_ring_color),
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.padding(top = 8.dp)
			)
			ColorSwatchRow(
				swatches =
					listOf(
						ColorSwatch("", Color(prefs.textColor()), stringResource(R.string.color_auto)),
						ColorSwatch("#FFFFFF", Color.White, "#FFFFFF"),
						ColorSwatch("#FFCDD2", Color(0xFFFFCDD2), "#FFCDD2"),
						ColorSwatch("#FFE082", Color(0xFFFFE082), "#FFE082"),
						ColorSwatch("#A5D6A7", Color(0xFFA5D6A7), "#A5D6A7"),
						ColorSwatch("#80DEEA", Color(0xFF80DEEA), "#80DEEA"),
						ColorSwatch("#90CAF9", Color(0xFF90CAF9), "#90CAF9"),
						ColorSwatch("#CE93D8", Color(0xFFCE93D8), "#CE93D8")
					),
				selected = prefs.cameraRingColor,
				onSelect = { vm.updatePrefs { copy(cameraRingColor = it) } }
			)
		}

		Text(
			text = stringResource(R.string.label_text_weight),
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(top = 8.dp)
		)
		val fontWeightLabels =
			listOf(
				stringResource(R.string.font_weight_normal),
				stringResource(R.string.font_weight_medium),
				stringResource(R.string.font_weight_bold)
			)
		SettingRadioRow(
			options = fontWeightLabels,
			selected = FONT_WEIGHT_IDS.indexOf(prefs.fontWeightName).coerceAtLeast(0),
			onSelect = { vm.updatePrefs { copy(fontWeightName = FONT_WEIGHT_IDS[it]) } }
		)
		SettingAction(stringResource(R.string.action_show_custom_bar), onShowBar)
		SettingAction(stringResource(R.string.action_hide_custom_bar)) { vm.setOverlayEnabled(false) }
	}
}

/** Stored ids behind the weight choices; indexes map to [dev.yoanndev90.statusbarhider.overlay.OverlayPrefs.fontWeightName]. */
private val FONT_WEIGHT_IDS = listOf("NORMAL", "MEDIUM", "BOLD")
