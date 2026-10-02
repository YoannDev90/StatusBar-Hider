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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
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
			text = stringResource(R.string.app_name),
			style = MaterialTheme.typography.headlineSmall,
			textAlign = TextAlign.Center
		)
		Text(
			text = state.oemName,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(bottom = if (state.oemUntested) 0.dp else 16.dp)
		)
		if (state.oemUntested) {
			Text(
				text = stringResource(R.string.header_untested),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.error,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(bottom = 16.dp)
			)
		}
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
	SectionHeader(stringResource(R.string.section_shizuku))
	SettingAction(stringResource(R.string.action_authorize_shizuku)) {
		vm.requestShizukuPermission(requestCode)
	}
}

@Composable
fun StatusBarSection(vm: MainViewModel) {
	SectionHeader(stringResource(R.string.section_status_bar))
	SettingAction(stringResource(R.string.action_hide_status_bar)) { vm.applyHide() }
	SettingAction(stringResource(R.string.action_check_state)) { vm.checkState() }
	SettingAction(stringResource(R.string.action_restore)) { vm.restore() }
}
