package dev.yoanndev90.statusbarhider.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** A titled card gathering related [content] rows into one visual block. */
@Composable
fun SettingGroup(
	@StringRes title: Int,
	content: @Composable ColumnScope.() -> Unit
) {
	Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
		) {
			Text(
				text = stringResource(title),
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(bottom = 4.dp)
			)
			content()
		}
	}
}

/** Card without a title, for blocks labelled inside their content. */
@Composable
fun SettingGroup(content: @Composable ColumnScope.() -> Unit) {
	Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
			content = content
		)
	}
}
