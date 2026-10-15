package dev.yoanndev90.statusbarhider.features.settings

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingInfoRow
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
			SettingGroup {
				SettingInfoRow(R.string.debug_manufacturer, Build.MANUFACTURER)
				SettingInfoRow(R.string.debug_model, Build.MODEL)
				SettingInfoRow(R.string.debug_android, Build.VERSION.RELEASE)
				SettingInfoRow(R.string.debug_sdk, "${Build.VERSION.SDK_INT}")
				SettingInfoRow(
					R.string.debug_build_type,
					stringResource(if (isDebuggable) R.string.debug_build_debug else R.string.debug_build_release)
				)
				SettingInfoRow(R.string.debug_app_version, packageInfo.versionName ?: "?")
				SettingInfoRow(R.string.debug_oem_config, "${state.oemName} (${state.oemId})")
			}
		}
	}
}
