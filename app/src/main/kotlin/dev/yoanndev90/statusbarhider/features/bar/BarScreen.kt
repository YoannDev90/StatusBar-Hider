package dev.yoanndev90.statusbarhider.features.bar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.features.bar.sections.AppsSection
import dev.yoanndev90.statusbarhider.features.bar.sections.BatterySection
import dev.yoanndev90.statusbarhider.features.bar.sections.BehaviorSection
import dev.yoanndev90.statusbarhider.features.bar.sections.ClockSection
import dev.yoanndev90.statusbarhider.features.bar.sections.ConnectivitySection
import dev.yoanndev90.statusbarhider.features.bar.sections.LockScreenSection
import dev.yoanndev90.statusbarhider.features.bar.sections.NotificationsSection
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/**
 * Custom bar tab: every widget toggle, grouped by theme. Groups follow
 * [dev.yoanndev90.statusbarhider.overlay.WidgetId.DEFAULT_ORDER] so the page reads
 * like the bar itself, left to right.
 */
@Composable
fun BarScreen(vm: PrefsViewModel) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	// This screen owns a PrefsViewModel too: without the collector any event it
	// emits (a missing overlay permission, for instance) would be dropped.
	HandlePrefsEvents(vm)

	SettingsScreen {
		ClockSection(prefs, vm)
		LockScreenSection(prefs, vm)
		BatterySection(prefs, vm)
		NotificationsSection(prefs, vm)
		ConnectivitySection(prefs, vm)
		AppsSection(prefs, vm)
		BehaviorSection(prefs, vm)
	}
}
