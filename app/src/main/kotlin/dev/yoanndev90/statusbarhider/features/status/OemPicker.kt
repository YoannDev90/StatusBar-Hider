package dev.yoanndev90.statusbarhider.features.status

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.oem.OemConfig

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
fun OemPicker(
	currentId: String,
	currentName: String,
	onSelect: (String) -> Unit
) {
	val context = LocalContext.current
	// Assets read once per composition subtree; the list only changes with an APK update.
	val options = remember(context) { oemOptions(context) }
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
