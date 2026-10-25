package dev.yoanndev90.statusbarhider.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingHint
import dev.yoanndev90.statusbarhider.ui.components.SettingSubRow
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** About: what this app is, where its code lives, and its privacy stance. Read-only — the control API and the setup checklist live in Debug. */
@Composable
fun AboutScreen() {
	val context = LocalContext.current
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
				AboutHeader(versionLabel)
			}
		}
		item {
			SettingGroup(R.string.about_group_links) {
				SettingSubRow(R.drawable.ic_open_with, R.string.about_source_code) { openUrl(context, URL_SOURCE) }
				SettingSubRow(R.drawable.ic_bug_report, R.string.about_report_issue) { openUrl(context, URL_ISSUES) }
				SettingSubRow(R.drawable.ic_fact_check, R.string.about_license) { openUrl(context, URL_LICENSE) }
			}
		}
		item {
			SettingGroup(R.string.about_group_credits) {
				SettingHint(R.string.about_credits)
				SettingHint(R.string.about_privacy, Modifier.padding(top = 8.dp))
			}
		}
	}
}

/** Identity block: the launcher glyph, the app name and the running build's version. */
@Composable
private fun AboutHeader(versionLabel: String) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(
			painter = painterResource(R.drawable.ic_launcher_foreground),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.primary,
			modifier = Modifier.size(48.dp)
		)
		Spacer(Modifier.width(16.dp))
		Column {
			Text(
				text = stringResource(R.string.app_name),
				style = MaterialTheme.typography.titleLarge
			)
			Text(
				text = versionLabel,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}

/** Opens [url] in the browser: links are the only reason this screen touches the network, and only through the user's tap. */
private fun openUrl(context: Context, url: String) {
	context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private const val URL_SOURCE = "https://github.com/YoannDev90/StatusBar-Hider"
private const val URL_ISSUES = "https://github.com/YoannDev90/StatusBar-Hider/issues"
private const val URL_LICENSE = "https://github.com/YoannDev90/StatusBar-Hider/blob/master/LICENSE"
