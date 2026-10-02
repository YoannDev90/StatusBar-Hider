package dev.yoanndev90.statusbarhider.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yoanndev90.statusbarhider.ui.sections.AppLogSections
import dev.yoanndev90.statusbarhider.ui.sections.HeaderSection
import dev.yoanndev90.statusbarhider.ui.sections.OrderAppearanceSection
import dev.yoanndev90.statusbarhider.ui.sections.OverlayTogglesSection
import dev.yoanndev90.statusbarhider.ui.sections.ShizukuSection
import dev.yoanndev90.statusbarhider.ui.sections.StatusBarSection

@Composable
fun MainScreen(
	vm: MainViewModel = viewModel(),
	shizukuRequestCode: Int,
	onCalendarToggle: (Boolean) -> Unit,
	onShowBar: () -> Unit,
	onHideFromLauncher: () -> Unit
) {
	Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
		Column(
			modifier =
				Modifier
					.padding(inner)
					.padding(horizontal = 24.dp)
					.verticalScroll(rememberScrollState())
		) {
			HeaderSection(vm)
			ShizukuSection(vm, shizukuRequestCode)
			StatusBarSection(vm)
			OverlayTogglesSection(vm, onCalendarToggle = onCalendarToggle)
			OrderAppearanceSection(vm, onShowBar = onShowBar)
			AppLogSections(vm, onHideFromLauncher = onHideFromLauncher)
		}
	}
}
