package dev.yoanndev90.statusbarhider.ui.sections

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.log.LogStore
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
	val clipboardLabel = stringResource(R.string.clipboard_label)
	val logsCopiedToast = stringResource(R.string.toast_logs_copied)
	val cannotShareToast = stringResource(R.string.toast_cannot_share_logs)

	SectionHeader(stringResource(R.string.section_app))
	SettingAction(stringResource(R.string.action_hide_from_launcher), onHideFromLauncher)
	SettingAction(stringResource(R.string.action_export_logs)) {
		val clip = ClipData.newPlainText(clipboardLabel, state.logs.joinToString("\n"))
		context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
		Toast.makeText(context, logsCopiedToast, Toast.LENGTH_SHORT).show()
	}
	SettingAction(stringResource(R.string.action_share_logs)) {
		val intent = LogStore.shareIntent(context)
		if (intent == null) {
			Toast.makeText(context, cannotShareToast, Toast.LENGTH_SHORT).show()
		} else {
			context.startActivity(Intent.createChooser(intent, null))
		}
	}

	SectionHeader(stringResource(R.string.section_log))
	SelectionContainer {
		Text(
			text = state.logs.joinToString("\n"),
			fontSize = 12.sp,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.fillMaxWidth()
		)
	}
}
