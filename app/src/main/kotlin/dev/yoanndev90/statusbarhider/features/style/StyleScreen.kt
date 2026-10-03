package dev.yoanndev90.statusbarhider.features.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.features.style.sections.CutoutSection
import dev.yoanndev90.statusbarhider.features.style.sections.LookSection
import dev.yoanndev90.statusbarhider.features.style.sections.WidgetOrderSection
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** Style tab: widget order, then the look of the custom bar (background, paddings, type, cutout). */
@Composable
fun StyleScreen(vm: StyleViewModel) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()

	SettingsScreen {
		WidgetOrderSection(prefs, vm)
		LookSection(prefs, vm)
		CutoutSection(prefs, vm)
	}
}
