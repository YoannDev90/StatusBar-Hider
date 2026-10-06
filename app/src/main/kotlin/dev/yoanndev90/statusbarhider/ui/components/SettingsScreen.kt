package dev.yoanndev90.statusbarhider.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Page layout shared by every settings screen: one lazily composed and
 * vertically scrollable column, so all tabs align and scroll the same way.
 *
 * Screens hand their groups over as `item { }` blocks. Navigation disposes the
 * page it leaves, so a `Column` re-composed and re-laid-out every group of the
 * target tab on every switch - including the ones below the fold, which was
 * most of the frame the switch cost.
 */
@Composable
fun SettingsScreen(content: LazyListScope.() -> Unit) {
	LazyColumn(
		modifier =
			Modifier
				.fillMaxSize()
				.padding(horizontal = 16.dp, vertical = 8.dp),
		content = content
	)
}
