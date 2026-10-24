package dev.yoanndev90.statusbarhider.features.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/**
 * Debug > Device info: who this phone is and which build is running.
 * Every row taps to copy its "label: value" line; "Copy all" takes the
 * whole block, ready to paste into a bug report.
 */
@Composable
fun DebugInfoScreen(vm: StatusViewModel) {
	val context = LocalContext.current
	val state by vm.uiState.collectAsStateWithLifecycle()
	val prefsState by vm.prefs.collectAsStateWithLifecycle()
	val shizukuVersion by vm.shizukuVersion.collectAsStateWithLifecycle()
	val sisterLabel by vm.sisterLabel.collectAsStateWithLifecycle()
	val packageInfo = remember {
		context.packageManager.getPackageInfo(context.packageName, 0)
	}
	val isDebuggable = remember {
		(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
	}

	val clipboardLabel = stringResource(R.string.clipboard_label)
	val copy: (String) -> Unit = { text ->
		val clip = ClipData.newPlainText(clipboardLabel, text)
		context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
		Toast.makeText(context, R.string.toast_info_copied, Toast.LENGTH_SHORT).show()
	}

	val config = LocalConfiguration.current
	val notInstalled = stringResource(R.string.debug_not_installed)
	val rows = buildList {
		add(stringResource(R.string.debug_manufacturer) to Build.MANUFACTURER)
		add(stringResource(R.string.debug_model) to Build.MODEL)
		add(stringResource(R.string.debug_android) to Build.VERSION.RELEASE)
		add(stringResource(R.string.debug_security_patch) to Build.VERSION.SECURITY_PATCH)
		add(stringResource(R.string.debug_sdk) to Build.VERSION.SDK_INT.toString())
		add(
			stringResource(R.string.debug_display) to
				"${config.screenWidthDp}×${config.screenHeightDp} dp @ ${config.densityDpi} dpi (×${config.densityDpi / 160f})"
		)
		add(stringResource(R.string.debug_locale) to config.locales[0].toString())
		if (Build.MANUFACTURER.contains("xiaomi", ignoreCase = true)) {
			add(stringResource(R.string.debug_miui_build) to Build.DISPLAY)
		}
		add(
			stringResource(R.string.debug_build_type) to
				stringResource(if (isDebuggable) R.string.debug_build_debug else R.string.debug_build_release)
		)
		add(
			stringResource(R.string.debug_app_version) to
				"${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})"
		)
		add(stringResource(R.string.debug_shizuku_version) to (shizukuVersion ?: notInstalled))
		add(stringResource(R.string.debug_sister_build) to (sisterLabel ?: notInstalled))
		val untested =
			if (state.oemUntested) " · ${stringResource(R.string.debug_untested)}" else ""
		add(stringResource(R.string.debug_oem_config) to "${state.oemName} (${state.oemId})$untested")
		val barState =
			stringResource(if (prefsState.enabled) R.string.debug_bar_enabled else R.string.debug_bar_disabled)
		add(stringResource(R.string.debug_custom_bar) to barState)
	}

	SettingsScreen {
		item {
			SettingGroup {
				rows.forEach { (label, value) -> CopyableInfoRow(label, value, copy) }
				SettingAction(R.drawable.ic_content_copy, R.string.action_copy_all) {
					copy(rows.joinToString("\n") { "${it.first}: ${it.second}" })
				}
			}
		}
	}
}

/** An info row that taps to copy; the icon hints the affordance. */
@Composable
private fun CopyableInfoRow(
	label: String,
	value: String,
	onCopy: (String) -> Unit
) {
	Row(
		modifier =
			Modifier
				.fillMaxWidth()
				.clickable { onCopy("$label: $value") }
				.padding(vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.weight(1f).padding(end = 12.dp)
		)
		Text(
			text = value,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f)
		)
		Icon(
			painter = painterResource(R.drawable.ic_content_copy),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(16.dp)
		)
	}
}
