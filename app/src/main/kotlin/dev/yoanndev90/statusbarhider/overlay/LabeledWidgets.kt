package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs

@Composable
internal fun CalendarWidget(
	prefs: OverlayPrefs,
	text: String,
	fg: Color
) {
	LabeledIcon(
		iconRes = R.drawable.ic_calendar,
		iconDesc = stringResource(R.string.cd_next_event),
		text = text,
		fg = fg,
		fontSize = prefs.textSp(-1),
		fontWeight = prefs.fontWeightValue,
		maxWidthDp = 160
	)
}

@Composable
internal fun MediaWidget(
	prefs: OverlayPrefs,
	text: String,
	fg: Color
) {
	LabeledIcon(
		iconRes = R.drawable.ic_media,
		iconDesc = stringResource(R.string.cd_now_playing),
		text = text,
		fg = fg,
		fontSize = prefs.textSp(-2),
		fontWeight = prefs.fontWeightValue,
		maxWidthDp = 180,
		startPadDp = 3
	)
}

@Composable
internal fun AlarmWidget(
	prefs: OverlayPrefs,
	text: String,
	fg: Color
) {
	LabeledIcon(
		iconRes = R.drawable.ic_alarm,
		iconDesc = stringResource(R.string.cd_next_alarm),
		text = text,
		fg = fg,
		fontSize = prefs.textSp(-2),
		fontWeight = prefs.fontWeightValue,
		startPadDp = 2
	)
}
