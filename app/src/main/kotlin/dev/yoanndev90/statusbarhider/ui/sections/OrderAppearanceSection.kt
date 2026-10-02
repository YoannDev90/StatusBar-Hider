package dev.yoanndev90.statusbarhider.ui.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.overlay.OverlayBackground
import dev.yoanndev90.statusbarhider.overlay.WidgetId
import dev.yoanndev90.statusbarhider.ui.MainViewModel
import dev.yoanndev90.statusbarhider.ui.components.SectionHeader
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingRadioRow
import dev.yoanndev90.statusbarhider.ui.components.SettingSlider
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

@Composable
fun OrderAppearanceSection(
	vm: MainViewModel,
	onShowBar: () -> Unit
) {
	val state by vm.uiState.collectAsStateWithLifecycle()
	val prefs = state.prefs
	val haptic = LocalHapticFeedback.current

	SectionHeader("WIDGET ORDER")
	Text(
		text = "Drag the handle to reorder",
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
							text = WidgetId.label(id),
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
								contentDescription = "Reorder"
							)
						}
					}
				}
			}
		}
	}

	SettingSwitch("Dark text (for transparent bar)", prefs.darkText) {
		vm.updatePrefs { copy(darkText = it) }
	}
	SettingRadioRow(
		options = listOf("Transparent", "Semi", "Black"),
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
	SettingSlider("Padding start", prefs.padStartDp, 0, 32, "dp") {
		vm.updatePrefs { copy(padStartDp = it) }
	}
	SettingSlider("Padding top", prefs.padTopDp, 0, 32, "dp") {
		vm.updatePrefs { copy(padTopDp = it) }
	}
	SettingSlider("Padding end", prefs.padEndDp, 0, 32, "dp") {
		vm.updatePrefs { copy(padEndDp = it) }
	}
	SettingSlider("Padding bottom", prefs.padBottomDp, 0, 32, "dp") {
		vm.updatePrefs { copy(padBottomDp = it) }
	}
	SettingAction("Show custom bar", onShowBar)
	SettingAction("Hide custom bar") { vm.setOverlayEnabled(false) }
}
