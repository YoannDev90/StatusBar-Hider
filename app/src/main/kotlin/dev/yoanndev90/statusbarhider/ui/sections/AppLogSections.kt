package dev.yoanndev90.statusbarhider.ui.sections

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.ui.MainViewModel
import dev.yoanndev90.statusbarhider.ui.components.SectionHeader
import dev.yoanndev90.statusbarhider.ui.components.SettingAction

@Composable
fun AppLogSections(
	vm: MainViewModel,
	onHideFromLauncher: () -> Unit
) {
	val state by vm.uiState.collectAsStateWithLifecycle()
	val context = LocalContext.current

	SectionHeader("APP")
	SettingAction("Hide from launcher", onHideFromLauncher)
	SettingAction("Export logs") {
		val clip = ClipData.newPlainText("StatusBarHider logs", state.logs.joinToString("\n"))
		context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
		Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
	}

	SectionHeader("LOG")
	SelectionContainer {
		Text(
			text = state.logs.joinToString("\n"),
			fontSize = 12.sp,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.fillMaxWidth()
		)
	}
}
