package dev.yoanndev90.statusbarhider.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** Placeholder for the future profiles feature: save and switch settings sets. */
@Composable
fun ProfilesScreen() {
	SettingsScreen {
		item {
			SettingGroup(R.string.cat_profiles) {
				Text(
					text = stringResource(R.string.hint_profiles_coming_soon),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
				)
			}
		}
	}
}
