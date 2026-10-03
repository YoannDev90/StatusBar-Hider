package dev.yoanndev90.statusbarhider.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R

@Composable
fun SettingSwitch(
	@StringRes label: Int,
	checked: Boolean,
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
		Text(
			text = stringResource(label),
			modifier = Modifier.weight(1f),
			style = MaterialTheme.typography.bodyLarge
		)
		Switch(
			checked = checked,
			onCheckedChange = null
		)
	}
}

/**
 * Slider labelled "label valueUnit".
 *
 * @param suffix optional unit resource (dp, sp, %, ...) appended to the value.
 */
@Composable
fun SettingSlider(
	@StringRes label: Int,
	value: Int,
	min: Int,
	max: Int,
	@StringRes suffix: Int = 0,
	onValueChange: (Int) -> Unit
) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Text(
			text =
				stringResource(
					R.string.slider_value,
					stringResource(label),
					value,
					if (suffix != 0) stringResource(suffix) else ""
				),
			style = MaterialTheme.typography.bodyMedium
		)
		Slider(
			value = value.toFloat(),
			onValueChange = { onValueChange(it.toInt().coerceIn(min, max)) },
			valueRange = min.toFloat()..max.toFloat(),
			steps = (max - min - 1).coerceAtLeast(0)
		)
	}
}

@Composable
fun SettingAction(
	@StringRes label: Int,
	onClick: () -> Unit
) {
	Button(
		onClick = onClick,
		modifier =
			Modifier
				.fillMaxWidth()
				.padding(vertical = 2.dp)
	) {
		Text(stringResource(label))
	}
}

@Composable
fun SettingRadioRow(
	options: List<String>,
	selected: Int,
	onSelect: (Int) -> Unit
) {
	Row(modifier = Modifier.fillMaxWidth()) {
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
					text = option,
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
