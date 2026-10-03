package dev.yoanndev90.statusbarhider.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A titled card gathering related [content] rows into one visual block. */
@Composable
fun SettingGroup(
	title: String,
	content: @Composable ColumnScope.() -> Unit
) {
	Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(bottom = 4.dp)
			)
			content()
		}
	}
}
