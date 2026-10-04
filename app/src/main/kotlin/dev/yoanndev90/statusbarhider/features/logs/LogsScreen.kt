package dev.yoanndev90.statusbarhider.features.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** Logs tab: export / share the app log, then the raw command output. */
@Composable
fun LogsScreen() {
	val logs by LogStore.lines.collectAsStateWithLifecycle()
	val context = LocalContext.current
	val clipboardLabel = stringResource(R.string.clipboard_label)
	val logsCopiedToast = stringResource(R.string.toast_logs_copied)
	val cannotShareToast = stringResource(R.string.toast_cannot_share_logs)

	SettingsScreen {
		SettingGroup(R.string.section_log) {
			SettingAction(R.drawable.ic_content_copy, R.string.action_export_logs) {
				val clip = ClipData.newPlainText(clipboardLabel, logs.joinToString("\n"))
				context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
				Toast.makeText(context, logsCopiedToast, Toast.LENGTH_SHORT).show()
			}
			SettingAction(R.drawable.ic_share, R.string.action_share_logs) {
				val intent = LogStore.shareIntent(context)
				if (intent == null) {
					Toast.makeText(context, cannotShareToast, Toast.LENGTH_SHORT).show()
				} else {
					context.startActivity(Intent.createChooser(intent, null))
				}
			}
			if (logs.isEmpty()) {
				Text(
					text = stringResource(R.string.log_empty),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 8.dp)
				)
			} else {
				HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
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
