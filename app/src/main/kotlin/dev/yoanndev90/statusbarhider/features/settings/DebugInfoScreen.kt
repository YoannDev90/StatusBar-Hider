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
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.core.settings.SettingsRegistry
import dev.yoanndev90.statusbarhider.core.settings.specs
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingInfoRow
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/**
 * Debug > Device info: who this phone is, which build is running, and the
 * settings that are not at their default — grouped by section, each with the
 * one-tap reset the spec describes (single setting or whole section).
 */
@Composable
fun DebugInfoScreen(vm: StatusViewModel) {
	val context = LocalContext.current
	val state by vm.uiState.collectAsStateWithLifecycle()
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	val packageInfo = remember {
		context.packageManager.getPackageInfo(context.packageName, 0)
	}
	val isDebuggable = remember {
		(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
	}
	// One group per section that still differs from its defaults.
	val modified = remember(prefs) { modifiedSections(prefs) }

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
		modified.forEach { (section, specs) ->
			item(key = "modified-${section.id}") {
				SettingGroup(section.title) {
					specs.forEach { spec ->
						// An enum spec may carry no row label of its own: its
						// values name it, so the debug list falls back to the key.
						SettingInfoRow(spec.label?.let { stringResource(it) } ?: spec.key, spec.get(prefs).toString())
						SettingAction(R.drawable.ic_refresh, R.string.action_reset) { vm.resetSpec(spec) }
					}
					SettingAction(R.drawable.ic_refresh, R.string.action_reset_settings) { vm.resetSection(section) }
				}
			}
		}
	}
}

/** Sections with at least one setting away from its default, with those settings. */
private fun modifiedSections(
	prefs: OverlayPrefs
): List<Pair<SettingSection<OverlayPrefs>, List<SettingSpec<*, OverlayPrefs>>>> =
	SettingsRegistry.sections.mapNotNull { section ->
		val modified = section.rows.specs().filter { it.isModifiedIn(prefs) }
		if (modified.isEmpty()) null else section to modified
	}
