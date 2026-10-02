package dev.yoanndev90.statusbarhider

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.yoanndev90.statusbarhider.overlay.OverlayBackground
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.WidgetId
import dev.yoanndev90.statusbarhider.ui.MainUiState
import dev.yoanndev90.statusbarhider.ui.MainViewModel
import dev.yoanndev90.statusbarhider.ui.sections.HeaderSection
import dev.yoanndev90.statusbarhider.ui.sections.OverlayTogglesSection
import dev.yoanndev90.statusbarhider.ui.sections.ShizukuSection
import dev.yoanndev90.statusbarhider.ui.sections.StatusBarSection
import dev.yoanndev90.statusbarhider.ui.theme.StatusBarHiderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
	companion object {
		private const val REQ_SHIZUKU = 1001
		private const val REQ_CALENDAR = 1002
	}

	private val vm: MainViewModel by viewModels()

	private var pendingCalendarToggle = false

	private lateinit var logView: TextView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		logView = findViewById(R.id.logView)

		findViewById<ComposeView>(R.id.composeTop).setContent {
			StatusBarHiderTheme {
				Column {
					HeaderSection(vm)
					ShizukuSection(vm, REQ_SHIZUKU)
					StatusBarSection(vm)
					OverlayTogglesSection(vm, onCalendarToggle = ::onCalendarToggle)
				}
			}
		}

		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch { vm.uiState.collect { render(it) } }
			}
		}

		findViewById<Button>(R.id.btnHideFromLauncher).setOnClickListener {
			val cn = ComponentName(this, MainActivity::class.java)
			packageManager.setComponentEnabledSetting(
				cn,
				PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
				PackageManager.DONT_KILL_APP
			)
			Toast
				.makeText(
					this,
					"Hidden! Access via Settings > Apps > StatusBar Hider",
					Toast.LENGTH_LONG
				).show()
		}

		findViewById<Button>(R.id.btnExportLogs).setOnClickListener {
			val clip = ClipData.newPlainText("StatusBarHider logs", logView.text)
			getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
			Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
		}

		val prefs = vm.uiState.value.prefs
		refreshOrderList()
		bindOverlayCheck(R.id.cbDarkText, prefs.darkText) { copy(darkText = it) }
		bindPaddingSeek(R.id.sbPadStart, R.id.tvPadStartVal, "Padding start", prefs.padStartDp) { copy(padStartDp = it) }
		bindPaddingSeek(R.id.sbPadTop, R.id.tvPadTopVal, "Padding top", prefs.padTopDp) { copy(padTopDp = it) }
		bindPaddingSeek(R.id.sbPadEnd, R.id.tvPadEndVal, "Padding end", prefs.padEndDp) { copy(padEndDp = it) }
		bindPaddingSeek(R.id.sbPadBottom, R.id.tvPadBottomVal, "Padding bottom", prefs.padBottomDp) { copy(padBottomDp = it) }

		findViewById<RadioGroup>(R.id.rgBackground).apply {
			check(
				when (prefs.background) {
					OverlayBackground.TRANSPARENT -> R.id.rbTransparent
					OverlayBackground.BLACK -> R.id.rbBlack
					else -> R.id.rbSemi
				}
			)
			setOnCheckedChangeListener { _, checkedId ->
				val bg =
					when (checkedId) {
						R.id.rbTransparent -> OverlayBackground.TRANSPARENT
						R.id.rbBlack -> OverlayBackground.BLACK
						else -> OverlayBackground.SEMI
					}
				vm.updatePrefs { copy(background = bg) }
			}
		}

		findViewById<Button>(R.id.btnShowCustomBar).setOnClickListener {
			if (!Settings.canDrawOverlays(this)) {
				Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show()
				startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
				return@setOnClickListener
			}
			vm.setOverlayEnabled(true)
		}
		findViewById<Button>(R.id.btnHideCustomBar).setOnClickListener {
			vm.setOverlayEnabled(false)
		}

		vm.refreshShizuku()
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		// Shizuku results are delivered via ShizukuRepository's listener, not here.
		if (requestCode == REQ_CALENDAR && pendingCalendarToggle) {
			pendingCalendarToggle = false
			val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
			if (granted) {
				vm.updatePrefs { copy(showCalendar = true) }
			} else {
				Toast.makeText(this, "Calendar permission denied", Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun render(state: MainUiState) {
		logView.text = state.logs.joinToString("\n")
	}

	private fun onCalendarToggle(checked: Boolean) {
		// Calendar needs a runtime permission: only save the toggle once granted.
		// The switch state comes from prefs, so a denial leaves it unchecked.
		if (checked && !hasCalendarPermission()) {
			pendingCalendarToggle = true
			requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), REQ_CALENDAR)
		} else {
			vm.updatePrefs { copy(showCalendar = checked) }
		}
	}

	private fun hasCalendarPermission(): Boolean =
		checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

	private fun bindOverlayCheck(
		id: Int,
		checked: Boolean,
		update: OverlayPrefs.(Boolean) -> OverlayPrefs
	) {
		findViewById<CheckBox>(id).apply {
			isChecked = checked
			setOnCheckedChangeListener { _, isChecked ->
				vm.updatePrefs { update(isChecked) }
			}
		}
	}

	private fun bindPaddingSeek(
		seekId: Int,
		labelId: Int,
		label: String,
		value: Int,
		update: OverlayPrefs.(Int) -> OverlayPrefs
	) {
		val seek = findViewById<SeekBar>(seekId)
		val tv = findViewById<TextView>(labelId)
		seek.progress = value.coerceIn(0, 32)
		tv.text = "$label: ${seek.progress}dp"
		seek.setOnSeekBarChangeListener(
			object : SeekBar.OnSeekBarChangeListener {
				override fun onProgressChanged(
					s: SeekBar,
					progress: Int,
					fromUser: Boolean
				) {
					tv.text = "$label: ${progress}dp"
					if (fromUser) vm.updatePrefs { update(progress) }
				}

				override fun onStartTrackingTouch(s: SeekBar) = Unit

				override fun onStopTrackingTouch(s: SeekBar) = Unit
			}
		)
	}

	private fun bindCountSeek(
		seekId: Int,
		labelId: Int,
		label: String,
		value: Int,
		min: Int,
		max: Int,
		suffix: String = "",
		update: OverlayPrefs.(Int) -> OverlayPrefs
	) {
		val seek = findViewById<SeekBar>(seekId)
		val tv = findViewById<TextView>(labelId)
		seek.max = max - min
		seek.progress = (value - min).coerceIn(0, max - min)
		tv.text = "$label ${seek.progress + min}$suffix"
		seek.setOnSeekBarChangeListener(
			object : SeekBar.OnSeekBarChangeListener {
				override fun onProgressChanged(
					s: SeekBar,
					progress: Int,
					fromUser: Boolean
				) {
					tv.text = "$label ${progress + min}$suffix"
					if (fromUser) vm.updatePrefs { update(progress + min) }
				}

				override fun onStartTrackingTouch(s: SeekBar) = Unit

				override fun onStopTrackingTouch(s: SeekBar) = Unit
			}
		)
	}

	private fun refreshOrderList() {
		val container = findViewById<LinearLayout>(R.id.orderList)
		container.removeAllViews()
		val order = vm.uiState.value.prefs.widgetOrder
			.toMutableList()
		order.forEachIndexed { index, id ->
			val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
			val label =
				TextView(this).apply {
					text = "${index + 1}. ${WidgetId.label(id)}"
					textSize = 14f
					layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
				}
			val up =
				Button(this).apply {
					text = "↑"
					setOnClickListener {
						if (index > 0) {
							val o = vm.uiState.value.prefs.widgetOrder
								.toMutableList()
							val tmp = o[index - 1]
							o[index - 1] = o[index]
							o[index] = tmp
							vm.updatePrefs { copy(widgetOrder = o) }
							refreshOrderList()
						}
					}
				}
			val down =
				Button(this).apply {
					text = "↓"
					setOnClickListener {
						val o = vm.uiState.value.prefs.widgetOrder
							.toMutableList()
						if (index < o.size - 1) {
							val tmp = o[index + 1]
							o[index + 1] = o[index]
							o[index] = tmp
							vm.updatePrefs { copy(widgetOrder = o) }
							refreshOrderList()
						}
					}
				}
			row.addView(label)
			row.addView(up)
			row.addView(down)
			container.addView(row)
		}
	}
}
