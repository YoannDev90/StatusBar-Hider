package dev.yoanndev90.statusbarhider.ui.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.overlay.OverlayBackground
import dev.yoanndev90.statusbarhider.overlay.WidgetId
import dev.yoanndev90.statusbarhider.ui.MainViewModel
import dev.yoanndev90.statusbarhider.ui.components.OrderRow
import dev.yoanndev90.statusbarhider.ui.components.SectionHeader
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingRadioRow
import dev.yoanndev90.statusbarhider.ui.components.SettingSlider
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch

@Composable
fun OrderAppearanceSection(
	vm: MainViewModel,
	onShowBar: () -> Unit
) {
	val state by vm.uiState.collectAsStateWithLifecycle()
	val prefs = state.prefs

	SectionHeader("WIDGET ORDER")
	Column {
		val order = prefs.widgetOrder
		order.forEachIndexed { index, id ->
			OrderRow(
				position = index + 1,
				label = WidgetId.label(id),
				canMoveUp = index > 0,
				canMoveDown = index < order.size - 1,
				onMoveUp = { vm.updatePrefs { copy(widgetOrder = order.swap(index, index - 1)) } },
				onMoveDown = { vm.updatePrefs { copy(widgetOrder = order.swap(index, index + 1)) } }
			)
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

private fun List<String>.swap(
	a: Int,
	b: Int
): List<String> =
	toMutableList().also {
		val tmp = it[a]
		it[a] = it[b]
		it[b] = tmp
	}
