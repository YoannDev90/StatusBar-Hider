package dev.yoanndev90.statusbarhider.ui.setup

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingStatusRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** First-run checklist; also reachable from Settings > About. */
@Composable
fun SetupScreen(
	vm: PrefsViewModel,
	onClose: (completed: Boolean) -> Unit
) {
	val context = LocalContext.current
	val shizuku by vm.shizuku.collectAsStateWithLifecycle()
	// Same contract as every other screen owning a PrefsViewModel.
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

	Column(
		modifier =
			Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		Text(
			text = stringResource(R.string.setup_subtitle),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
		)

		SettingGroup(R.string.section_shizuku) {
			SettingStatusRow(
				label = R.string.setup_shizuku,
				granted = shizuku == ShizukuState.READY,
				actionLabel = R.string.action_authorize_shizuku
			) { vm.requestShizukuPermission() }
		}

		val loaded = grants
		if (loaded == null) {
			Box(
				modifier = Modifier.fillMaxWidth().padding(16.dp),
				contentAlignment = Alignment.Center
			) {
				CircularProgressIndicator(Modifier.size(24.dp))
			}
		} else {
			SettingGroup(R.string.setup_group_permissions) {
				SettingStatusRow(
					label = R.string.setup_overlay,
					granted = loaded.overlay
				) { PermissionAccess.openSpecialAccess(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }
				SettingStatusRow(
					label = R.string.setup_notif_access,
					granted = loaded.notifListener
				) { PermissionAccess.openSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
				SettingStatusRow(
					label = R.string.setup_calendar,
					granted = loaded.calendar
				) { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }
				if (Build.VERSION.SDK_INT >= 33) {
					SettingStatusRow(
						label = R.string.setup_post_notifs,
						granted = loaded.postNotifs
					) { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
				}
				SettingStatusRow(
					label = R.string.setup_usage,
					granted = loaded.usage
				) { PermissionAccess.openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS) }
			}
		}

		Spacer(Modifier.size(8.dp))
		SettingAction(R.drawable.ic_check, R.string.action_setup_done) { onClose(true) }
		TextButton(
			onClick = { onClose(false) },
			modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
		) {
			Text(stringResource(R.string.action_setup_later))
		}
	}
}
