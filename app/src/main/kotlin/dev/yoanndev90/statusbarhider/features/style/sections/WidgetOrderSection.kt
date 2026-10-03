package dev.yoanndev90.statusbarhider.features.style.sections

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.style.StyleViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.WidgetId
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

/** Drag-to-reorder list defining the widget order of the bar, left to right. */
@Composable
fun WidgetOrderSection(
	prefs: OverlayPrefs,
	vm: StyleViewModel
) {
	val haptic = LocalHapticFeedback.current

	SettingGroup(R.string.section_widget_order) {
		Text(
			text = stringResource(R.string.label_widget_order_hint),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(bottom = 4.dp)
		)
		ReorderableColumn(
			list = prefs.widgetOrder,
			onSettle = { fromIndex, toIndex -> vm.moveWidget(fromIndex, toIndex) },
			onMove = { haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) },
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
										onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) }
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
	}
}
