package dev.yoanndev90.statusbarhider.features.logs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingHint
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/**
 * Logs: the raw command output. Copy and share live in the shell's top bar.
 */
@Composable
fun LogsScreen(vm: LogsViewModel) {
	val logs by vm.lines.collectAsStateWithLifecycle()

	SettingsScreen {
		item {
			SettingGroup {
				if (logs.isEmpty()) {
					SettingHint(R.string.log_empty, Modifier.padding(top = 8.dp))
				} else {
					SelectionContainer {
						Text(
							text = logs.joinToString("\n"),
							fontSize = 12.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.fillMaxWidth()
						)
					}
				}
			}
		}
	}
}
