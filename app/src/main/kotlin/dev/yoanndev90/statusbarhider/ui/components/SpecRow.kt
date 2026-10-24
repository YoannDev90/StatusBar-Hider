package dev.yoanndev90.statusbarhider.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.BoolSpec
import dev.yoanndev90.statusbarhider.core.settings.EnumSpec
import dev.yoanndev90.statusbarhider.core.settings.IntSpec
import dev.yoanndev90.statusbarhider.core.settings.ListSpec
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.core.settings.TextSpec
import dev.yoanndev90.statusbarhider.core.settings.ValueSpec

/**
 * Draws one spec from its own metadata. The `when` is exhaustive over the
 * sealed [SettingSpec]: a new spec type without a renderer is a compile
 * error, not a silently missing row.
 *
 * [update] applies a transform when the write lands — the same shape as
 * `PrefsViewModel.updatePrefs` — so a value derived from [prefs] can never
 * overwrite a change that arrived after the last recomposition.
 *
 * A spec whose shownWhen is false draws nothing here as well: the section
 * already filters it out of its visibleRows, and both read the same
 * predicate, so the two cannot disagree.
 */
@Composable
fun <T, S : Any> SpecRow(spec: SettingSpec<T, S>, prefs: S, update: ((S) -> S) -> Unit) {
	if (!spec.shownWhen(prefs)) return
	// Marked with a dot while away from its default, on every control type.
	val modified = spec.isModifiedIn(prefs)
	when (spec) {
		is BoolSpec ->
			SettingSwitch(
				icon = requireNotNull(spec.icon) { "'${spec.key}' rendered without an icon" },
				label = requireNotNull(spec.label) { "'${spec.key}' rendered without a label" },
				checked = spec.get(prefs),
				subtitle = spec.subtitle ?: 0,
				modified = modified
			) { value -> update { spec.set(it, value) } }
		is IntSpec ->
			SettingSlider(
				icon = requireNotNull(spec.icon) { "'${spec.key}' rendered without an icon" },
				label = requireNotNull(spec.label) { "'${spec.key}' rendered without a label" },
				value = spec.get(prefs),
				min = spec.min,
				max = spec.max,
				suffix = spec.suffix ?: 0,
				subtitle = spec.subtitle ?: 0,
				modified = modified
			) { value -> update { spec.set(it, value) } }
		is EnumSpec -> {
			// SettingRadioRow has no label slot: a spec that carries one shows
			// it as the caption above the choices (font weight), one without
			// stays captionless (background).
			spec.label?.let { SettingLabelRow(it) }
			SettingRadioRow(
				icon = requireNotNull(spec.icon) { "'${spec.key}' rendered without an icon" },
				options = spec.entries.map { it.second },
				selected = spec.entries.indexOfFirst { it.first == spec.get(prefs) }.coerceAtLeast(0),
				modified = modified
			) { index -> update { spec.set(it, spec.entries[index].first) } }
		}
		is TextSpec -> TextSpecRow(spec, prefs, update)
		// Lists and free shapes have no generic control: the section carries
		// them inside a Custom row (section tests forbid them as OfSpec).
		is ListSpec, is ValueSpec -> Unit
	}
}

/**
 * Text field with a local draft and a Set button: the draft is saveable and
 * keyed on the stored value, so it survives a rotation but resets when the
 * value changes elsewhere. Screens needing live feedback (toasts, format
 * probes) keep their own hand-written row instead.
 */
@Composable
private fun <S : Any> TextSpecRow(spec: TextSpec<S>, prefs: S, update: ((S) -> S) -> Unit) {
	val current = spec.get(prefs)
	var draft by rememberSaveable(current) { mutableStateOf(current) }
	Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		SettingModifiedDot(spec.isModifiedIn(prefs))
		OutlinedTextField(
			value = draft,
			onValueChange = { draft = it },
			label = { Text(stringResource(requireNotNull(spec.label) { "'${spec.key}' rendered without a label" })) },
			leadingIcon = spec.icon?.let { icon ->
				{
					Icon(
						painter = painterResource(icon),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			},
			singleLine = true,
			modifier = Modifier.weight(1f).padding(end = 8.dp)
		)
		Button(onClick = { update { spec.set(it, draft) } }, enabled = spec.validate(draft)) {
			Text(stringResource(R.string.action_set))
		}
	}
}
