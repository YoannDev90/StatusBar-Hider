package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.model.OverlayBarState

@Composable
internal fun NotifWidget(
	prefs: OverlayPrefs,
	state: OverlayBarState,
	fg: Color
) {
	if (!prefs.showNotifs || !state.notifsEnabled || state.notifs.isEmpty()) return
	val icons = state.notifs.take(prefs.maxNotifs.coerceIn(1, 8))
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(3.dp)
	) {
		for (entry in icons) {
			key(entry.pkg) {
				if (entry.bitmap != null) {
					Image(
						bitmap = entry.bitmap,
						contentDescription = entry.pkg,
						modifier = Modifier.size(16.dp)
					)
				} else {
					IconImage(R.drawable.ic_signal, entry.pkg, fg)
				}
			}
		}
	}
}
