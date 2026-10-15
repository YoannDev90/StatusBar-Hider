package dev.yoanndev90.statusbarhider.features.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.data.AppSettings
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingHint
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** About: version, the control API token and the setup checklist entry point. */
@Composable
fun AboutScreen(
	vm: StatusViewModel,
	onOpenSetup: () -> Unit
) {
	val context = LocalContext.current
	HandlePrefsEvents(vm)
	val packageInfo = remember {
		context.packageManager.getPackageInfo(context.packageName, 0)
	}
	val isDebuggable = remember {
		(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
	}
	val versionLabel =
		stringResource(
			R.string.about_version,
			packageInfo.versionName ?: "?",
			stringResource(if (isDebuggable) R.string.debug_build_debug else R.string.debug_build_release)
		)

	SettingsScreen {
		item {
			SettingGroup {
				SettingHint(versionLabel)
				SettingAction(R.drawable.ic_key, R.string.action_copy_control_token) { copyControlToken(context) }
				SettingHint(R.string.hint_control_token, Modifier.padding(bottom = 8.dp))
				SettingAction(R.drawable.ic_settings, R.string.action_open_setup) { onOpenSetup() }
			}
		}
	}
}

/** Copies the control API token so it can be pasted into an adb or Tasker intent. */
private fun copyControlToken(context: Context) {
	val clipboard = context.getSystemService(ClipboardManager::class.java)
	clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), AppSettings.controlToken(context)))
	Toast.makeText(context, R.string.toast_token_copied, Toast.LENGTH_SHORT).show()
}
