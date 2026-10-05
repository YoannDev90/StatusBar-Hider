package dev.yoanndev90.statusbarhider.ui.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.usage.UsageAccess
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.overlay.NotifListenerService
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup

private const val TAG = "SetupScreen"

/** First-run checklist; also reachable from the Status tab. */
@Composable
fun SetupScreen(
	vm: SetupViewModel,
	onClose: (completed: Boolean) -> Unit
) {
	val context = LocalContext.current
	val shizuku by vm.shizuku.collectAsStateWithLifecycle()
	// Bumped on every resume: the grants happen in other screens, so the rows
	// are re-read when the user comes back. `key` is what subscribes this
	// composition to the tick.
	var resumeTick by remember { mutableStateOf(0) }
	LifecycleResumeEffect(Unit) {
		resumeTick++
		onPauseOrDispose { }
	}

	val calendarLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
	val notificationsLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }

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

		key(resumeTick) {
			SettingGroup(R.string.section_shizuku) {
				SetupRow(
					label = R.string.setup_shizuku,
					done = shizuku == ShizukuState.READY,
					actionLabel = R.string.action_authorize_shizuku
				) { vm.requestShizukuPermission() }
			}

			SettingGroup(R.string.setup_group_permissions) {
				SetupRow(
					label = R.string.setup_overlay,
					done = Settings.canDrawOverlays(context),
					actionLabel = R.string.action_grant
				) { openSpecialAccess(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }
				SetupRow(
					label = R.string.setup_notif_access,
					done = NotifListenerService.isEnabled(context),
					actionLabel = R.string.action_grant
				) { openSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
				SetupRow(
					label = R.string.setup_calendar,
					done = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
					actionLabel = R.string.action_grant
				) { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }
				if (Build.VERSION.SDK_INT >= 33) {
					SetupRow(
						label = R.string.setup_post_notifs,
						done = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
						actionLabel = R.string.action_grant
					) { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
				}
				SetupRow(
					label = R.string.setup_usage,
					done = UsageAccess.granted(context),
					actionLabel = R.string.action_grant
				) { openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS) }
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

/** One checklist line: status icon, label, and a Grant button while missing. */
@Composable
private fun SetupRow(
	@StringRes label: Int,
	done: Boolean,
	@StringRes actionLabel: Int,
	onClick: () -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(
			painter = painterResource(if (done) R.drawable.ic_check_circle else R.drawable.ic_close),
			contentDescription = stringResource(if (done) R.string.cd_granted else R.string.cd_missing),
			tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
			modifier = Modifier.size(20.dp)
		)
		Text(
			text = stringResource(label),
			style = MaterialTheme.typography.bodyLarge,
			color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f).padding(start = 12.dp)
		)
		if (!done) {
			TextButton(onClick = onClick) { Text(stringResource(actionLabel)) }
		}
	}
}

/** Special-access screens take a package URI; the rest are plain settings pages. */
private fun openSpecialAccess(
	context: Context,
	action: String
) {
	try {
		context.startActivity(Intent(action, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
	} catch (e: Exception) {
		Log.w(TAG, "openSpecialAccess", e)
		LogStore.appendOnce(
			context,
			"$TAG#special#$action",
			context.getString(R.string.log_error, "$action: ${e.message}")
		)
	}
}

private fun openSettings(
	context: Context,
	action: String
) {
	try {
		context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
	} catch (e: Exception) {
		Log.w(TAG, "openSettings", e)
		LogStore.appendOnce(
			context,
			"$TAG#settings#$action",
			context.getString(R.string.log_error, "$action: ${e.message}")
		)
	}
}
