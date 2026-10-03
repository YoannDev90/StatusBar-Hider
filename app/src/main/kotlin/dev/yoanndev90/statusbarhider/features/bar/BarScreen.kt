package dev.yoanndev90.statusbarhider.features.bar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.features.bar.sections.BatterySection
import dev.yoanndev90.statusbarhider.features.bar.sections.BehaviorSection
import dev.yoanndev90.statusbarhider.features.bar.sections.ClockSection
import dev.yoanndev90.statusbarhider.features.bar.sections.ConnectivitySection
import dev.yoanndev90.statusbarhider.features.bar.sections.NotificationsSection
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/**
 * Custom bar tab: every widget toggle, grouped by theme. Groups follow
 * [dev.yoanndev90.statusbarhider.overlay.WidgetId.DEFAULT_ORDER] so the page reads
 * like the bar itself, left to right.
 */
@Composable
fun BarScreen(vm: BarViewModel) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()

	SettingsScreen {
		ClockSection(prefs, vm)
		BatterySection(prefs, vm)
		NotificationsSection(prefs, vm)
		ConnectivitySection(prefs, vm)
		BehaviorSection(prefs, vm)
	}
}
