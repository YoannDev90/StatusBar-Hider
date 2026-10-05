package dev.yoanndev90.statusbarhider.features.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.features.style.sections.CutoutSection
import dev.yoanndev90.statusbarhider.features.style.sections.LookSection
import dev.yoanndev90.statusbarhider.features.style.sections.TestSection
import dev.yoanndev90.statusbarhider.features.style.sections.WidgetOrderSection
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** Style tab: widget order, then the look of the custom bar (background, paddings, type, cutout) and tests. */
@Composable
fun StyleScreen(vm: PrefsViewModel) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	HandlePrefsEvents(vm)

	SettingsScreen {
		WidgetOrderSection(prefs, vm)
		LookSection(prefs, vm)
		CutoutSection(prefs, vm)
		TestSection(prefs, vm)
	}
}
