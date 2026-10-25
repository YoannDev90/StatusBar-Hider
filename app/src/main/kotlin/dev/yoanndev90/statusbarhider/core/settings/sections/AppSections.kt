package dev.yoanndev90.statusbarhider.core.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.permission.PermissionAccess
import dev.yoanndev90.statusbarhider.core.permission.PermissionGrants
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.Subtitle
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.ui.screens.status.StatusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Screens of the settings home that are still hand-written: the lock hub and
 * the whole app group. They carry no rows yet — the section only feeds the
 * navigation line, the top-bar title and the search index. When a screen
 * moves to its own store (app flags, OEM commands), it gains the rows that
 * replace its hand-written body.
 */

object LockSection : SettingSection<OverlayPrefs>(
	id = "lock",
	icon = R.drawable.ic_lock,
	title = R.string.cat_lock_screen,
	subtitle = Subtitle.Res(R.string.cat_lock_screen_sub),
	group = SectionGroup.CUSTOM_BAR,
	keywords = listOf("keyguard", "locked")
)

object ProfilesSection : SettingSection<OverlayPrefs>(
	id = "profiles",
	icon = R.drawable.ic_person,
	title = R.string.cat_profiles,
	subtitle = Subtitle.Res(R.string.cat_profiles_sub),
	group = SectionGroup.APP,
	keywords = listOf("profil", "preset", "snapshot", "them", "look", "day", "night")
)

object OemSection : SettingSection<OverlayPrefs>(
	id = "oem",
	icon = R.drawable.ic_devices,
	title = R.string.cat_oem,
	subtitle = Subtitle.Res(R.string.cat_oem_sub),
	group = SectionGroup.APP,
	keywords = listOf("shizuku", "hide", "restore", "status", "bar", "sony", "samsung", "xiaomi")
)

object DebugSection : SettingSection<OverlayPrefs>(
	id = "debug",
	icon = R.drawable.ic_bug_report,
	title = R.string.cat_debug,
	subtitle = Subtitle.Res(R.string.cat_debug_sub),
	group = SectionGroup.APP,
	keywords = listOf("device", "permissions", "token", "control", "tasker", "setup", "checklist"),
	badge = { vm -> debugGrantBadge(vm) }
)

object DebugInfoSection : SettingSection<OverlayPrefs>(
	id = "debug/info",
	icon = R.drawable.ic_info,
	title = R.string.debug_sub_info,
	subtitle = Subtitle.Res(R.string.debug_sub_info_sub),
	group = SectionGroup.APP,
	keywords = listOf("device", "model", "manufacturer", "version", "sdk", "build"),
	listed = false
)

object DebugPermissionsSection : SettingSection<OverlayPrefs>(
	id = "debug/permissions",
	icon = R.drawable.ic_check_circle,
	title = R.string.debug_sub_permissions,
	subtitle = Subtitle.Res(R.string.debug_sub_permissions_sub),
	group = SectionGroup.APP,
	keywords = listOf("permission", "shizuku", "overlay", "calendar", "accessibility", "grant", "usage", "notification"),
	listed = false
)

object DebugDiffSection : SettingSection<OverlayPrefs>(
	id = "debug/diff",
	icon = R.drawable.ic_query_stats,
	title = R.string.debug_sub_diff,
	subtitle = Subtitle.Res(R.string.debug_sub_diff_sub),
	group = SectionGroup.APP,
	keywords = listOf("modified", "default", "reset", "diff", "custom", "changed"),
	listed = false
)

object LogsSection : SettingSection<OverlayPrefs>(
	id = "logs",
	icon = R.drawable.ic_nav_log,
	title = R.string.cat_logs,
	subtitle = Subtitle.Res(R.string.cat_logs_sub),
	group = SectionGroup.APP,
	keywords = listOf("log", "output", "command", "debug")
)

object BackupSection : SettingSection<OverlayPrefs>(
	id = "backup",
	icon = R.drawable.ic_save_alt,
	title = R.string.cat_backup,
	subtitle = Subtitle.Res(R.string.cat_backup_sub),
	group = SectionGroup.APP,
	keywords = listOf("export", "import", "json")
)

object AboutSection : SettingSection<OverlayPrefs>(
	id = "about",
	icon = R.drawable.ic_info,
	title = R.string.cat_about,
	subtitle = Subtitle.Res(R.string.cat_about_sub),
	group = SectionGroup.APP,
	keywords = listOf("version", "license", "github", "credits", "privacy", "changelog")
)

/**
 * Missing-grant count shown on the Debug row. Grants change in other screens,
 * so the state is recomputed on every resume rather than cached. Null while
 * the first read is in flight — the row keeps its plain subtitle meanwhile.
 */
@Composable
private fun debugGrantBadge(vm: StatusViewModel): String? {
	val context = LocalContext.current
	val shizukuReady = vm.shizuku.collectAsStateWithLifecycle().value == ShizukuState.READY
	var resumeTick by remember { mutableStateOf(0) }
	LifecycleResumeEffect(Unit) {
		resumeTick++
		onPauseOrDispose { }
	}
	val grants by produceState<PermissionGrants?>(null, resumeTick) {
		value = withContext(Dispatchers.IO) { PermissionAccess.grants(context) }
	}
	val missing = grants?.let { missingGrantCount(shizukuReady, it) } ?: return null
	return if (missing > 0) stringResource(R.string.settings_missing_grants, missing) else null
}

/** How many of the grants the app needs are missing; shizuku included. */
fun missingGrantCount(
	shizukuReady: Boolean,
	grants: PermissionGrants
): Int = listOf(shizukuReady, grants.overlay, grants.notifListener, grants.calendar, grants.postNotifs, grants.usage).count { !it }
