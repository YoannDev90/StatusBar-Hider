package dev.yoanndev90.statusbarhider.features.settings

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** Debug > Device info: who this phone is and which build is running. */
@Composable
fun DebugInfoScreen(vm: StatusViewModel) {
	val context = LocalContext.current
	val state by vm.uiState.collectAsStateWithLifecycle()
	val packageInfo = remember {
		context.packageManager.getPackageInfo(context.packageName, 0)
	}
	val isDebuggable = remember {
		(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
	}

	SettingsScreen {
		item {
			SettingGroup(R.string.debug_sub_info) {
				InfoRow(R.string.debug_manufacturer, Build.MANUFACTURER)
				InfoRow(R.string.debug_model, Build.MODEL)
				InfoRow(R.string.debug_android, Build.VERSION.RELEASE)
				InfoRow(R.string.debug_sdk, "${Build.VERSION.SDK_INT}")
				InfoRow(
					R.string.debug_build_type,
					stringResource(if (isDebuggable) R.string.debug_build_debug else R.string.debug_build_release)
				)
				InfoRow(R.string.debug_app_version, packageInfo.versionName ?: "?")
				InfoRow(R.string.debug_oem_config, "${state.oemName} (${state.oemId})")
			}
		}
	}
}

/** Label on the left, value on the right; long values wrap under the label. */
@Composable
private fun InfoRow(
	labelRes: Int,
	value: String
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
	) {
		Text(
			text = stringResource(labelRes),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.weight(1f).padding(end = 12.dp)
		)
		Text(
			text = value,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f)
		)
	}
}
