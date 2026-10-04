package dev.yoanndev90.statusbarhider.features.style.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontStyle
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
	val order = prefs.widgetOrder

	SettingGroup(R.string.section_widget_order) {
		Text(
			text = stringResource(R.string.label_widget_order_hint),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(bottom = 6.dp)
		)

		// Mini bar: the widgets as chips, in the order the bar draws them.
		OrderPreview(order, Modifier.padding(bottom = 8.dp))

		ReorderableColumn(
			list = order,
			onSettle = { fromIndex, toIndex -> vm.moveWidget(fromIndex, toIndex) },
			onMove = { haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) },
			verticalArrangement = Arrangement.spacedBy(0.dp)
		) { index, id, isDragging ->
			key(id) {
				ReorderableItem {
					val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp)
					val spacer = id == WidgetId.SPACER
					Column {
						// Own separator, dropped while this row is the one floating.
						if (index > 0 && !isDragging) {
							HorizontalDivider(
								thickness = 1.dp,
								color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
							)
						}
						Surface(
							color =
								if (isDragging) {
									MaterialTheme.colorScheme.surfaceContainerHigh
								} else {
									Color.Transparent
								},
							shadowElevation = elevation,
							shape = RoundedCornerShape(if (isDragging) 10.dp else 0.dp)
						) {
							Row(
								modifier =
									Modifier
										.fillMaxWidth()
										.height(48.dp)
										.padding(horizontal = 4.dp)
										// The whole row is the handle: long press, then drag.
										.longPressDraggableHandle(
											onDragStarted = {
												haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
											},
											onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) }
										),
								verticalAlignment = Alignment.CenterVertically
							) {
								// Position: row 1 is the leftmost widget on the bar.
								Text(
									text = "${index + 1}",
									style = MaterialTheme.typography.labelMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.width(26.dp)
								)
								Text(
									text = stringResource(WidgetId.labelRes(id)),
									style = MaterialTheme.typography.bodyLarge,
									color =
										if (spacer) {
											MaterialTheme.colorScheme.onSurfaceVariant
										} else {
											MaterialTheme.colorScheme.onSurface
										},
									fontStyle = if (spacer) FontStyle.Italic else null,
									modifier = Modifier.weight(1f)
								)
								Icon(
									imageVector = Icons.Filled.Menu,
									contentDescription = stringResource(R.string.cd_reorder),
									tint = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
					}
				}
			}
		}
	}
}

/** Chip strip mirroring the bar itself: same widgets, same left-to-right order. */
@Composable
private fun OrderPreview(
	order: List<String>,
	modifier: Modifier = Modifier
) {
	LazyRow(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		contentPadding = PaddingValues(horizontal = 4.dp)
	) {
		itemsIndexed(order, key = { _, id -> id }) { _, id ->
			val spacer = id == WidgetId.SPACER
			Surface(
				color = MaterialTheme.colorScheme.surfaceContainerHigh,
				shape = RoundedCornerShape(8.dp)
			) {
				Text(
					text = stringResource(WidgetId.labelRes(id)),
					style = MaterialTheme.typography.labelMedium,
					color =
						if (spacer) {
							MaterialTheme.colorScheme.onSurfaceVariant
						} else {
							MaterialTheme.colorScheme.onSurface
						},
					fontStyle = if (spacer) FontStyle.Italic else null,
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
				)
			}
		}
	}
}
