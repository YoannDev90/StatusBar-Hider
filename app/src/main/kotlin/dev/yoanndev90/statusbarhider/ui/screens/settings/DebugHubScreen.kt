package dev.yoanndev90.statusbarhider.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.data.AppFlagsStore
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingHint
import dev.yoanndev90.statusbarhider.ui.components.SettingSubRow
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen
import dev.yoanndev90.statusbarhider.ui.shell.Routes

/** Debug hub: the sub-screens, the control API token and the setup checklist. */
@Composable
fun DebugHubScreen(
	onNavigate: (String) -> Unit,
	onOpenSetup: () -> Unit
) {
	val context = LocalContext.current
	SettingsScreen {
		item {
			SettingGroup {
				SettingSubRow(R.drawable.ic_info, R.string.debug_sub_info) { onNavigate(Routes.DEBUG_INFO) }
				SettingSubRow(R.drawable.ic_check_circle, R.string.debug_sub_permissions) { onNavigate(Routes.DEBUG_PERMISSIONS) }
				SettingSubRow(R.drawable.ic_query_stats, R.string.debug_sub_diff) { onNavigate(Routes.DEBUG_DIFF) }
			}
		}
		item {
			SettingGroup(R.string.section_control_api) {
				SettingAction(R.drawable.ic_key, R.string.action_copy_control_token) { copyControlToken(context) }
				SettingHint(R.string.hint_control_token)
			}
		}
		item {
			SettingGroup {
				SettingAction(R.drawable.ic_settings, R.string.action_open_setup) { onOpenSetup() }
			}
		}
	}
}

/** Copies the control API token so it can be pasted into an adb or Tasker intent. */
private fun copyControlToken(context: Context) {
	val clipboard = context.getSystemService(ClipboardManager::class.java)
	clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), AppFlagsStore.getInstance(context).controlToken()))
	Toast.makeText(context, R.string.toast_token_copied, Toast.LENGTH_SHORT).show()
}
