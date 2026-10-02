package dev.yoanndev90.statusbarhider.ui.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.ui.MainViewModel
import dev.yoanndev90.statusbarhider.ui.components.SectionHeader
import dev.yoanndev90.statusbarhider.ui.components.SettingAction

@Composable
fun HeaderSection(vm: MainViewModel) {
	val state by vm.uiState.collectAsStateWithLifecycle()

	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Text(
			text = "StatusBar Hider",
			style = MaterialTheme.typography.headlineSmall,
			textAlign = TextAlign.Center
		)
		Text(
			text = state.oemName,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(bottom = 16.dp)
		)
		Text(
			text = state.shizukuText,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.fillMaxWidth()
		)
	}
}

@Composable
fun ShizukuSection(
	vm: MainViewModel,
	requestCode: Int
) {
	SectionHeader("SHIZUKU")
	SettingAction("Authorize Shizuku") { vm.requestShizukuPermission(requestCode) }
}

@Composable
fun StatusBarSection(vm: MainViewModel) {
	SectionHeader("STATUS BAR")
	SettingAction("Hide status bar") { vm.applyHide() }
	SettingAction("Check state") { vm.checkState() }
	SettingAction("Restore (undo)") { vm.restore() }
}
