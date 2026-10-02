package dev.yoanndev90.statusbarhider.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun SectionHeader(title: String) {
	Text(
		text = title,
		style = MaterialTheme.typography.labelMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
	)
}

@Composable
fun SettingSwitch(
	label: String,
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
			text = label,
			modifier = Modifier.weight(1f),
			style = MaterialTheme.typography.bodyLarge
		)
		Switch(
			checked = checked,
			onCheckedChange = null
		)
	}
}

@Composable
fun SettingSlider(
	label: String,
	value: Int,
	min: Int,
	max: Int,
	suffix: String = "",
	onValueChange: (Int) -> Unit
) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Text(
			text = "$label $value$suffix",
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
	label: String,
	onClick: () -> Unit
) {
	Button(
		onClick = onClick,
		modifier =
			Modifier
				.fillMaxWidth()
				.padding(vertical = 2.dp)
	) {
		Text(label)
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

@Composable
fun OrderRow(
	position: Int,
	label: String,
	canMoveUp: Boolean,
	canMoveDown: Boolean,
	onMoveUp: () -> Unit,
	onMoveDown: () -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = "$position. $label",
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f)
		)
		Button(
			onClick = onMoveUp,
			enabled = canMoveUp,
			modifier = Modifier.padding(end = 4.dp)
		) {
			Text("↑")
		}
		Button(
			onClick = onMoveDown,
			enabled = canMoveDown
		) {
			Text("↓")
		}
	}
	Spacer(Modifier.height(2.dp))
}
