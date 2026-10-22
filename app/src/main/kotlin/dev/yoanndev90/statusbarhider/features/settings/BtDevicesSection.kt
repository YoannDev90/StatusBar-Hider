package dev.yoanndev90.statusbarhider.features.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.overlay.BtDeviceKind
import dev.yoanndev90.statusbarhider.overlay.BtDevicePreview
import dev.yoanndev90.statusbarhider.overlay.BtSource
import dev.yoanndev90.statusbarhider.overlay.SystemIndicators
import dev.yoanndev90.statusbarhider.overlay.connectedBluetoothPreviews
import dev.yoanndev90.statusbarhider.overlay.kindFromOverrideName
import dev.yoanndev90.statusbarhider.ui.components.SettingHint
import dev.yoanndev90.statusbarhider.ui.components.SettingLabelRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Connected-device list under the per-device icons switch: every device
 * shows the icon the bar would draw and which signal picked it; tapping a
 * row forces a kind (stored per MAC in the prefs) or restores detection.
 */
@Composable
internal fun BtDevicesSection(
	overrides: Map<String, String>,
	onOverride: (mac: String, kindName: String?) -> Unit
) {
	val app = LocalContext.current.applicationContext
	val permission =
		Build.VERSION.SDK_INT < 31 ||
			app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

	// Loaded once per entry; override changes re-render locally, not through a reload.
	val devices by produceState<List<BtDevicePreview>?>(initialValue = null, key1 = permission) {
		value =
			if (!permission) {
				emptyList()
			} else {
				val indicators = SystemIndicators(app)
				try {
					withContext(Dispatchers.IO) { indicators.connectedBluetoothPreviews(overrides) }
				} finally {
					indicators.release()
				}
			}
	}
	Column(Modifier.fillMaxWidth()) {
		SettingLabelRow(R.string.bt_devices_title, R.drawable.ic_bt)
		val loaded = devices
		when {
			!permission -> SettingHint(R.string.bt_devices_permission)
			loaded == null -> SettingHint(R.string.bt_devices_loading)
			loaded.isEmpty() -> SettingHint(R.string.bt_devices_none)
			else -> loaded.forEach { BtDeviceRow(it, overrides, onOverride) }
		}
	}
}

@Composable
private fun BtDeviceRow(
	device: BtDevicePreview,
	overrides: Map<String, String>,
	onOverride: (String, String?) -> Unit
) {
	var menu by remember { mutableStateOf(false) }

	// The override applies here instantly; the persisted list catches up on reload.
	val overridden = kindFromOverrideName(overrides[device.mac])
	val kind = overridden ?: device.kind
	val source = if (overridden != null) BtSource.OVERRIDE else device.source
	Row(
		modifier =
			Modifier
				.fillMaxWidth()
				.clickable { menu = true }
				.padding(vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(
			painter = painterResource(kind.icon().icon),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(20.dp)
		)
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			Text(
				text = device.name,
				style = MaterialTheme.typography.bodyLarge
			)
			SettingHint(sourceLabel(source))
		}
		DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
			DropdownMenuItem(
				text = { Text(stringResource(R.string.bt_override_auto)) },
				onClick = {
					onOverride(device.mac, null)
					menu = false
				}
			)
			BtDeviceKind.entries
				.filter { it != BtDeviceKind.UNKNOWN }
				.forEach { candidate ->
					DropdownMenuItem(
						text = { Text(stringResource(candidate.menuLabel())) },
						onClick = {
							onOverride(device.mac, candidate.name)
							menu = false
						}
					)
				}
			DropdownMenuItem(
				text = { Text(stringResource(R.string.bt_override_generic)) },
				onClick = {
					onOverride(device.mac, BtDeviceKind.UNKNOWN.name)
					menu = false
				}
			)
		}
	}
}

@Composable
private fun sourceLabel(source: BtSource): String =
	stringResource(
		when (source) {
			BtSource.OVERRIDE -> R.string.bt_source_override
			BtSource.FAST_PAIR -> R.string.bt_source_fast_pair
			BtSource.CLASS -> R.string.bt_source_class
		}
	)
