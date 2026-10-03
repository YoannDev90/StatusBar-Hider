package dev.yoanndev90.statusbarhider.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.OemConfig
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.ui.MainViewModel
import dev.yoanndev90.statusbarhider.ui.components.SectionHeader
import dev.yoanndev90.statusbarhider.ui.components.SettingAction

/** Status bar page: device identification, Shizuku authorization and hide / restore commands. */
@Composable
fun StatusScreen(
	vm: MainViewModel,
	shizukuRequestCode: Int
) {
	Column(
		modifier =
			Modifier
				.fillMaxSize()
				.padding(horizontal = 24.dp)
				.verticalScroll(rememberScrollState())
	) {
		HeaderSection(vm)
		ShizukuSection(vm, shizukuRequestCode)
		StatusBarSection(vm)
	}
}

@Composable
private fun HeaderSection(vm: MainViewModel) {
	val state by vm.uiState.collectAsStateWithLifecycle()

	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Text(
			text = state.oemName,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(top = 8.dp, bottom = if (state.oemUntested) 0.dp else 16.dp)
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
private fun ShizukuSection(
	vm: MainViewModel,
	requestCode: Int
) {
	val state by vm.uiState.collectAsStateWithLifecycle()

	SectionHeader(stringResource(R.string.section_shizuku))
	SettingAction(stringResource(R.string.action_authorize_shizuku)) {
		vm.requestShizukuPermission(requestCode)
	}
	if (state.shizuku != ShizukuState.READY) {
		Text(
			text = stringResource(R.string.hint_authorize_shizuku),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
		)
	}
}

@Composable
private fun StatusBarSection(vm: MainViewModel) {
	val state by vm.uiState.collectAsStateWithLifecycle()
	val context = LocalContext.current
	// Assets read once per composition subtree; the list only changes with an APK update.
	val options = remember(context) { oemOptions(context) }

	SectionHeader(stringResource(R.string.section_status_bar))
	OemPicker(
		currentId = state.oemId,
		currentName = state.oemName,
		options = options,
		onSelect = vm::selectOem
	)
	SettingAction(stringResource(R.string.action_redetect_oem)) { vm.redetectOem() }
	SettingAction(stringResource(R.string.action_hide_status_bar)) { vm.applyHide() }
	SettingAction(stringResource(R.string.action_check_state)) { vm.checkState() }
	SettingAction(stringResource(R.string.action_restore)) { vm.restore() }
}

private data class OemOption(
	val id: String,
	val name: String
)

private fun oemOptions(context: Context): List<OemOption> =
	OemConfig
		.listAvailable(context)
		.map { id -> OemOption(id, OemConfig.load(context, id).name) }

/** Dropdown over every shipped OEM config; the button shows the active one. */
@Composable
private fun OemPicker(
	currentId: String,
	currentName: String,
	options: List<OemOption>,
	onSelect: (String) -> Unit
) {
	var expanded by remember { mutableStateOf(false) }
	Box(modifier = Modifier.fillMaxWidth()) {
		Button(
			onClick = { expanded = true },
			modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
		) {
			Text(stringResource(R.string.label_oem_config, currentName))
		}
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			options.forEach { option ->
				DropdownMenuItem(
					text = { Text("${option.name} (${option.id})") },
					trailingIcon = {
						if (option.id == currentId) {
							Icon(
								imageVector = Icons.Default.Check,
								contentDescription = null
							)
						}
					},
					onClick = {
						expanded = false
						onSelect(option.id)
					}
				)
			}
		}
	}
}
