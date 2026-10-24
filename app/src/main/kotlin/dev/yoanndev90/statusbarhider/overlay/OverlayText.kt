package dev.yoanndev90.statusbarhider.overlay

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.settings.BarFontWeight
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "OverlayBar"

/** Base text size with a relative offset, in sp (e.g. clock = base, date = base - 1). */
internal fun OverlayPrefs.textSp(delta: Int = 0): TextUnit = (fontSizeSp + delta).coerceIn(8, 28).sp

internal val OverlayPrefs.fontWeightValue: FontWeight
	get() =
		when (fontWeightName) {
			BarFontWeight.BOLD -> FontWeight.Bold
			BarFontWeight.MEDIUM -> FontWeight.Medium
			BarFontWeight.NORMAL -> FontWeight.Normal
		}

/** Ticks aligned on [intervalMs] boundaries while [enabled]; frozen otherwise. */
@Composable
internal fun rememberTick(
	intervalMs: Long,
	enabled: Boolean
): Long {
	var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
	LaunchedEffect(intervalMs, enabled) {
		if (!enabled) return@LaunchedEffect
		// Refresh before the first wait: `now` still holds the pre-sleep value,
		// and the first boundary can be up to a full interval away.
		now = System.currentTimeMillis()
		while (true) {
			val t = System.currentTimeMillis()
			delay(((t / intervalMs + 1) * intervalMs - t).coerceAtLeast(0))
			now = System.currentTimeMillis()
		}
	}
	return now
}

internal val tnum = TextStyle(fontFeatureSettings = "tnum")

/**
 * Renders [pattern] at [tick], logging a bad pattern once instead of letting
 * the frame crash on every recomposition.
 */
@Composable
internal fun formattedText(
	tick: Long,
	pattern: String,
	what: String
): String {
	val context = LocalContext.current
	val errorFormat = stringResource(R.string.log_error)
	return remember(tick, pattern) {
		try {
			SimpleDateFormat(pattern, Locale.getDefault()).format(Date(tick))
		} catch (e: Exception) {
			Log.w(TAG, "$what format", e)
			LogStore.appendOnce(
				context,
				"$TAG#${what}Format#$pattern",
				String.format(errorFormat, "bad $what format: ${e.message}")
			)
			""
		}
	}
}

/** Shared clock/date typography: one line, optional click, tabular figures when asked. */
@Composable
internal fun WidgetText(
	prefs: OverlayPrefs,
	text: String,
	fontSize: TextUnit,
	fg: Color,
	numeric: Boolean,
	onClick: () -> Unit
) {
	Text(
		text = text,
		fontSize = fontSize,
		fontWeight = prefs.fontWeightValue,
		color = fg,
		style = if (numeric) tnum else LocalTextStyle.current,
		maxLines = 1,
		overflow = TextOverflow.Ellipsis,
		modifier =
			Modifier.then(
				if (prefs.interactive) Modifier.clickable(onClick = onClick) else Modifier
			)
	)
}

@Composable
internal fun IconImage(
	res: Int,
	desc: String,
	tint: Color,
	sizeDp: Int = 16
) {
	Image(
		painter = painterResource(res),
		contentDescription = desc,
		colorFilter = ColorFilter.tint(tint),
		modifier = Modifier.size(sizeDp.dp)
	)
}

@Composable
internal fun LabeledIcon(
	iconRes: Int,
	iconDesc: String,
	text: String,
	fg: Color,
	fontSize: TextUnit,
	fontWeight: FontWeight,
	maxWidthDp: Int? = null,
	startPadDp: Int = 4
) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		IconImage(iconRes, iconDesc, fg, 14)
		Text(
			text = text,
			fontSize = fontSize,
			fontWeight = fontWeight,
			color = fg,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier =
				Modifier
					.padding(start = startPadDp.dp)
					.then(if (maxWidthDp != null) Modifier.widthIn(max = maxWidthDp.dp) else Modifier)
		)
	}
}
