package dev.yoanndev90.statusbarhider.ui.components

import androidx.annotation.DrawableRes
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R

/**
 * One checklist line that opens a system screen instead of granting a
 * permission: icon, label, optional hint and an Open button.
 *
 * Deliberately carries no status mark: the OEM autostart / battery toggles it
 * reaches are private AppOps with no read API, so a check or a cross here
 * would be a guess. [SettingStatusRow] is for the grants that can be answered.
 */
@Composable
fun SettingOpenRow(
	label: String,
	hint: String = "",
	@DrawableRes icon: Int = R.drawable.ic_settings,
	onClick: () -> Unit
) {
	Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically
		) {
			// Duplicated from SettingRows' SettingIcon, which is private there;
			// that file already sits at detekt's per-file function ceiling.
			Icon(
				painter = painterResource(icon),
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(20.dp)
			)
			Spacer(Modifier.width(12.dp))
			Text(
				text = label,
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f)
			)
			TextButton(onClick = onClick) { Text(stringResource(R.string.action_open)) }
		}
		if (hint.isNotEmpty()) {
			// Starts at 32dp so it sits under the label, not under the icon.
			Text(
				text = hint,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(start = 32.dp, end = 16.dp)
			)
		}
	}
}
