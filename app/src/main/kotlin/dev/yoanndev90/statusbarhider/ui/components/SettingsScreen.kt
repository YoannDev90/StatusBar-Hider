package dev.yoanndev90.statusbarhider.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Page layout shared by every settings screen: one vertically scrollable
 * column with the app gutter, so all tabs align and scroll the same way.
 */
@Composable
fun SettingsScreen(content: @Composable ColumnScope.() -> Unit) {
	Column(
		modifier =
			Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 16.dp, vertical = 8.dp),
		content = content
	)
}
