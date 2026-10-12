package dev.yoanndev90.statusbarhider.features.settings

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.permission.PermissionAccess
import dev.yoanndev90.statusbarhider.core.permission.PermissionGrants
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Debug > Permissions: every grant the app relies on, allowed or blocked, with a shortcut to fix it. */
@Composable
fun DebugPermissionsScreen(vm: StatusViewModel) {
	val context = LocalContext.current
	val shizuku by vm.shizuku.collectAsStateWithLifecycle()
	HandlePrefsEvents(vm)
	// Bumped on every resume: the grants happen in other screens, so the rows
	// are re-read when the user comes back. The tick is the key of that read.
	var resumeTick by remember { mutableStateOf(0) }
	LifecycleResumeEffect(Unit) {
		resumeTick++
		onPauseOrDispose { }
	}

	val calendarLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
	val notificationsLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
	// The permission rows are binder calls (overlay, listener, AppOps, grants):
	// read them once per resume on IO instead of on every recomposition. Values
	// from the previous read stay on screen while a refresh is in flight.
	val grants by produceState<PermissionGrants?>(null, resumeTick) {
		value = withContext(Dispatchers.IO) { PermissionAccess.grants(context) }
	}

	SettingsScreen {
		item {
			SettingGroup(R.string.debug_sub_permissions) {
				PermissionRow(
					label = R.string.perm_shizuku,
					granted = shizuku == ShizukuState.READY
				) { vm.requestShizukuPermission() }
				val loaded = grants ?: return@SettingGroup
				PermissionRow(
					label = R.string.perm_overlay,
					granted = loaded.overlay
				) { PermissionAccess.openSpecialAccess(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }
				PermissionRow(
					label = R.string.perm_notif_access,
					granted = loaded.notifListener
				) { PermissionAccess.openSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
				PermissionRow(
					label = R.string.perm_calendar,
					granted = loaded.calendar
				) { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }
				if (Build.VERSION.SDK_INT >= 33) {
					PermissionRow(
						label = R.string.perm_post_notifs,
						granted = loaded.postNotifs
					) { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
				}
				PermissionRow(
					label = R.string.perm_usage,
					granted = loaded.usage
				) { PermissionAccess.openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS) }
				PermissionRow(
					label = R.string.perm_accessibility,
					granted = loaded.lockOverlay
				) { PermissionAccess.openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
			}
		}
	}
}

/** One permission line: label, allowed / blocked status, and a Grant button while missing. */
@Composable
private fun PermissionRow(
	@StringRes label: Int,
	granted: Boolean,
	onClick: () -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = stringResource(label),
			style = MaterialTheme.typography.bodyLarge,
			modifier = Modifier.weight(1f)
		)
		Text(
			text = stringResource(if (granted) R.string.perm_status_allowed else R.string.perm_status_blocked),
			style = MaterialTheme.typography.bodySmall,
			color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
			modifier = Modifier.padding(end = 8.dp)
		)
		if (!granted) {
			TextButton(onClick = onClick) { Text(stringResource(R.string.action_grant)) }
		}
	}
}
