package dev.yoanndev90.statusbarhider.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R

@Composable
private fun SettingIcon(
	@DrawableRes icon: Int
) {
	Icon(
		painter = painterResource(icon),
		contentDescription = null,
		tint = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.size(20.dp)
	)
}

@Composable
fun SettingSwitch(
	@DrawableRes icon: Int,
	@StringRes label: Int,
	checked: Boolean,
	@StringRes subtitle: Int = 0,
	onCheckedChange: (Boolean) -> Unit
) {
	Row(
		modifier =
			Modifier
				.fillMaxWidth()
				.selectable(
					selected = checked,
					onClick = { onCheckedChange(!checked) },
					role = Role.Switch
				).padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		SettingIcon(icon)
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			Text(
				text = stringResource(label),
				style = MaterialTheme.typography.bodyLarge
			)
			if (subtitle != 0) {
				Text(
					text = stringResource(subtitle),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
		Switch(
			checked = checked,
			onCheckedChange = null
		)
	}
}

/**
 * Slider with the live value pinned to the right of the label, so dragging
 * never reflows the row. [subtitle] carries the optional range or caveat.
 *
 * @param suffix optional unit resource (dp, sp, %, ...) appended to the value.
 */
@Composable
fun SettingSlider(
	@DrawableRes icon: Int,
	@StringRes label: Int,
	value: Int,
	min: Int,
	max: Int,
	@StringRes suffix: Int = 0,
	@StringRes subtitle: Int = 0,
	onValueChange: (Int) -> Unit
) {
	Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
		SettingIcon(icon)
		Spacer(Modifier.width(12.dp))
		Column(modifier = Modifier.weight(1f)) {
			Row(modifier = Modifier.fillMaxWidth()) {
				Text(
					text = stringResource(label),
					modifier = Modifier.weight(1f),
					style = MaterialTheme.typography.bodyMedium
				)
				Text(
					text = stringResource(R.string.slider_value, value, if (suffix != 0) stringResource(suffix) else ""),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			if (subtitle != 0) {
				Text(
					text = stringResource(subtitle),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			Slider(
				value = value.toFloat(),
				onValueChange = { onValueChange(it.toInt().coerceIn(min, max)) },
				valueRange = min.toFloat()..max.toFloat(),
				steps = (max - min - 1).coerceAtLeast(0)
			)
		}
	}
}

@Composable
fun SettingAction(
	@DrawableRes icon: Int,
	@StringRes label: Int,
	danger: Boolean = false,
	onClick: () -> Unit
) {
	Button(
		onClick = onClick,
		colors =
			if (danger) {
				ButtonDefaults.buttonColors(
					containerColor = MaterialTheme.colorScheme.errorContainer,
					contentColor = MaterialTheme.colorScheme.onErrorContainer
				)
			} else {
				ButtonDefaults.buttonColors()
			},
		modifier =
			Modifier
				.fillMaxWidth()
				.padding(vertical = 2.dp)
	) {
		Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
		Spacer(Modifier.width(8.dp))
		Text(stringResource(label))
	}
}

/**
 * Navigation row for the Settings list: icon, title, optional subtitle and a
 * chevron; the whole row navigates to [route] via [onNavigate]. Titles are
 * pre-resolved strings (the root screen resolves them once for search too).
 * [errorSubtitle] paints the subtitle in the error color for attention states.
 */
@Composable
fun SettingNavRow(
	@DrawableRes icon: Int,
	title: String,
	subtitle: String,
	route: String,
	onNavigate: (String) -> Unit,
	errorSubtitle: Boolean = false
) {
	Row(
		modifier =
			Modifier
				.fillMaxWidth()
				.clickable { onNavigate(route) }
				.padding(vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		SettingIcon(icon)
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyLarge
			)
			Text(
				text = subtitle,
				style = MaterialTheme.typography.bodySmall,
				color = if (errorSubtitle) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		Icon(
			painter = painterResource(R.drawable.ic_chevron_right),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(20.dp)
		)
	}
}

/** Compact navigation row for sub-screens (Lock screen, Debug hubs). */
@Composable
fun SettingSubRow(
	@DrawableRes icon: Int,
	@StringRes title: Int,
	onClick: () -> Unit
) {
	Row(
		modifier =
			Modifier
				.fillMaxWidth()
				.clickable(onClick = onClick)
				.padding(vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		SettingIcon(icon)
		Spacer(Modifier.width(12.dp))
		Text(
			text = stringResource(title),
			modifier = Modifier.weight(1f),
			style = MaterialTheme.typography.bodyLarge
		)
		Icon(
			painter = painterResource(R.drawable.ic_chevron_right),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(20.dp)
		)
	}
}

/**
 * Secondary explanatory text under a row: one styling and one padding for
 * every hint in the app, instead of a per-screen hand-rolled Text.
 */
@Composable
fun SettingHint(
	@StringRes text: Int,
	modifier: Modifier = Modifier
) {
	Text(
		text = stringResource(text),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = modifier.fillMaxWidth()
	)
}

/** Same as [SettingHint] for texts built at runtime (selected app names, summaries). */
@Composable
fun SettingHint(
	text: String,
	modifier: Modifier = Modifier
) {
	Text(
		text = text,
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = modifier.fillMaxWidth()
	)
}

/** Section label row for controls that have no switch of their own; [icon] aligns it with the rows below. */
@Composable
fun SettingLabelRow(
	@StringRes label: Int,
	@DrawableRes icon: Int = 0
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		if (icon != 0) {
			SettingIcon(icon)
			Spacer(Modifier.width(12.dp))
		}
		Text(
			text = stringResource(label),
			style = MaterialTheme.typography.bodyMedium
		)
	}
}

/**
 * One permission line: status icon, label, and a Grant button while missing.
 * Shared by the setup checklist and the debug permission board.
 */
@Composable
fun SettingStatusRow(
	@StringRes label: Int,
	granted: Boolean,
	@StringRes actionLabel: Int = R.string.action_grant,
	onClick: () -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(
			painter = painterResource(if (granted) R.drawable.ic_check_circle else R.drawable.ic_close),
			contentDescription = stringResource(if (granted) R.string.cd_granted else R.string.cd_missing),
			tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
			modifier = Modifier.size(20.dp)
		)
		Text(
			text = stringResource(label),
			style = MaterialTheme.typography.bodyLarge,
			color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f).padding(start = 12.dp)
		)
		if (!granted) {
			TextButton(onClick = onClick) { Text(stringResource(actionLabel)) }
		}
	}
}

/** Read-only label on the left, value on the right; long values wrap under the label. */
@Composable
fun SettingInfoRow(
	@StringRes label: Int,
	value: String
) {
	Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
		Text(
			text = stringResource(label),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.weight(1f).padding(end = 12.dp)
		)
		Text(
			text = value,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f)
		)
	}
}

@Composable
fun SettingRadioRow(
	@DrawableRes icon: Int,
	@StringRes options: List<Int>,
	selected: Int,
	onSelect: (Int) -> Unit
) {
	Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		SettingIcon(icon)
		Spacer(Modifier.width(12.dp))
		options.forEachIndexed { index, option ->
			Row(
				modifier =
					Modifier
						.weight(1f)
						.selectable(
							selected = index == selected,
							onClick = { onSelect(index) },
							role = Role.RadioButton
						).padding(vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				RadioButton(
					selected = index == selected,
					onClick = null
				)
				Spacer(Modifier.width(4.dp))
				Text(
					text = stringResource(option),
					modifier = Modifier.weight(1f),
					maxLines = 1,
					style = MaterialTheme.typography.bodyMedium
				)
			}
		}
	}
}

/** One selectable color preset: the stored [value] ("" = follow the bar text color) and its preview. */
data class ColorSwatch(
	val value: String,
	val color: Color,
	val label: String
)

/** Horizontal row of circular color presets; the selected one gets an accent ring. */
@Composable
fun ColorSwatchRow(
	swatches: List<ColorSwatch>,
	selected: String,
	onSelect: (String) -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		swatches.forEach { swatch ->
			val isSelected = swatch.value == selected
			Box(
				modifier =
					Modifier
						.padding(end = 8.dp)
						.size(28.dp)
						.clip(CircleShape)
						.background(swatch.color)
						.border(
							width = 2.dp,
							color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
							shape = CircleShape
						).selectable(
							selected = isSelected,
							onClick = { onSelect(swatch.value) },
							role = Role.RadioButton
						).semantics { contentDescription = swatch.label }
			)
		}
	}
}
