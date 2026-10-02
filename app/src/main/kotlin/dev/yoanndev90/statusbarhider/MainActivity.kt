package dev.yoanndev90.statusbarhider

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
	companion object {
		private const val REQ_SHIZUKU = 1001
		private const val REQ_CALENDAR = 1002
	}

	private var pendingCalendarToggle = false

	private lateinit var statusView: TextView
	private lateinit var logView: TextView
	private lateinit var oem: OemConfig

	private val binderListener = Shizuku.OnBinderReceivedListener { refreshStatus() }
	private val deadListener =
		Shizuku.OnBinderDeadListener {
			runOnUiThread { statusView.text = "Shizuku: disconnected" }
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		val oemId = OemConfig.detect(this)
		OemConfig.saveId(this, oemId)
		oem = OemConfig.load(this, oemId)

		statusView = findViewById(R.id.statusView)
		logView = findViewById(R.id.logView)
		findViewById<TextView>(R.id.oemNameText).text = oem.name

		findViewById<Button>(R.id.btnHideStatusBar).apply {
			setBackgroundColor(Color.parseColor("#1a73e8"))
			setTextColor(Color.WHITE)
		}

		findViewById<Button>(R.id.btnAuthorizeShizuku).setOnClickListener {
			if (!Shizuku.pingBinder()) {
				appendLog("Shizuku is not running. Start it, then try again.")
				return@setOnClickListener
			}
			if (ShizukuCmd.granted()) {
				appendLog("Already authorized.")
			} else {
				Shizuku.requestPermission(REQ_SHIZUKU)
			}
		}

		findViewById<Button>(R.id.btnHideStatusBar).setOnClickListener {
			runAsync("applying hide...") { applyHide() }
		}
		findViewById<Button>(R.id.btnCheckState).setOnClickListener {
			runAsync("reading state...") { showState() }
		}
		findViewById<Button>(R.id.btnRestore).setOnClickListener {
			runAsync("restoring...") { restore() }
		}

		findViewById<Button>(R.id.btnHideFromLauncher).setOnClickListener {
			val cn = ComponentName(this, MainActivity::class.java)
			packageManager.setComponentEnabledSetting(
				cn,
				android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
				android.content.pm.PackageManager.DONT_KILL_APP
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

		val prefs = OverlayPrefs.load(this)
		bindOverlayCheck(R.id.cbShowSeconds, prefs.showSeconds) { copy(showSeconds = it) }
		bindOverlayCheck(R.id.cbBattery, prefs.showBattery) { copy(showBattery = it) }
		bindOverlayCheck(R.id.cbBatteryPct, prefs.showBatteryPct) { copy(showBatteryPct = it) }
		bindOverlayCheck(R.id.cbBatteryIcon, prefs.showBatteryIcon) { copy(showBatteryIcon = it) }
		bindOverlayCheck(R.id.cbDate, prefs.showDate) { copy(showDate = it) }
		findViewById<EditText>(R.id.etDateFormat).setText(prefs.dateFormat)
		findViewById<Button>(R.id.btnDateFormat).setOnClickListener {
			val fmt = findViewById<EditText>(R.id.etDateFormat).text.toString().ifEmpty { "EEE dd MMM" }
			try {
				java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault()).format(java.util.Date())
			} catch (_: Exception) {
				Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}
			updateOverlayPrefs { copy(dateFormat = fmt) }
			appendLog("Date format: $fmt")
		}
		bindOverlayCheck(R.id.cbNotifs, prefs.showNotifs) { copy(showNotifs = it) }
		findViewById<Button>(R.id.btnNotifAccess).setOnClickListener {
			try {
				startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
			} catch (_: Exception) {
				Toast.makeText(this, "Cannot open notification settings", Toast.LENGTH_SHORT).show()
			}
		}
		bindCountSeek(
			R.id.sbMaxNotifs,
			R.id.tvMaxNotifsVal,
			"Max notification icons",
			prefs.maxNotifs,
			1,
			8
		) { copy(maxNotifs = it) }
		bindOverlayCheck(R.id.cbWifi, prefs.showWifi) { copy(showWifi = it) }
		bindOverlayCheck(R.id.cbMobile, prefs.showMobileData) { copy(showMobileData = it) }
		bindOverlayCheck(R.id.cbBluetooth, prefs.showBluetooth) { copy(showBluetooth = it) }
		bindOverlayCheck(R.id.cbAirplane, prefs.showAirplane) { copy(showAirplane = it) }
		bindOverlayCheck(R.id.cbUsb, prefs.showUsb) { copy(showUsb = it) }
		bindOverlayCheck(R.id.cbAlarm, prefs.showAlarm) { copy(showAlarm = it) }
		bindOverlayCheck(R.id.cbMedia, prefs.showMedia) { copy(showMedia = it) }
		bindOverlayCheck(R.id.cbNfc, prefs.showNfc) { copy(showNfc = it) }
		bindOverlayCheck(R.id.cbGps, prefs.showGps) { copy(showGps = it) }
		// Calendar needs a runtime permission: only save the toggle once granted.
		findViewById<CheckBox>(R.id.cbCalendar).apply {
			isChecked = prefs.showCalendar
			setOnCheckedChangeListener { box, isChecked ->
				if (isChecked && !hasCalendarPermission()) {
					pendingCalendarToggle = true
					requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), REQ_CALENDAR)
				} else {
					updateOverlayPrefs { copy(showCalendar = isChecked) }
				}
			}
		}
		bindOverlayCheck(R.id.cbBandwidth, prefs.showBandwidth) { copy(showBandwidth = it) }
		bindOverlayCheck(R.id.cbBandwidthMerged, prefs.bandwidthMerged) { copy(bandwidthMerged = it) }
		bindOverlayCheck(R.id.cbInteractive, prefs.interactive) { copy(interactive = it) }
		bindCountSeek(
			R.id.sbBurnIn,
			R.id.tvBurnInVal,
			"Burn-in shift: every",
			prefs.burnInMin,
			0,
			30,
			" min (0 = off)"
		) { copy(burnInMin = it) }
		refreshOrderList()
		bindOverlayCheck(R.id.cbDarkText, prefs.darkText) { copy(darkText = it) }
		bindPaddingSeek(R.id.sbPadStart, R.id.tvPadStartVal, "Padding start", prefs.padStartDp) { copy(padStartDp = it) }
		bindPaddingSeek(R.id.sbPadTop, R.id.tvPadTopVal, "Padding top", prefs.padTopDp) { copy(padTopDp = it) }
		bindPaddingSeek(R.id.sbPadEnd, R.id.tvPadEndVal, "Padding end", prefs.padEndDp) { copy(padEndDp = it) }
		bindPaddingSeek(R.id.sbPadBottom, R.id.tvPadBottomVal, "Padding bottom", prefs.padBottomDp) { copy(padBottomDp = it) }

		findViewById<RadioGroup>(R.id.rgBackground).apply {
			check(
				when (prefs.background) {
					dev.yoanndev90.statusbarhider.overlay.OverlayBackground.TRANSPARENT -> R.id.rbTransparent
					dev.yoanndev90.statusbarhider.overlay.OverlayBackground.BLACK -> R.id.rbBlack
					else -> R.id.rbSemi
				}
			)
			setOnCheckedChangeListener { _, checkedId ->
				val bg =
					when (checkedId) {
						R.id.rbTransparent -> dev.yoanndev90.statusbarhider.overlay.OverlayBackground.TRANSPARENT
						R.id.rbBlack -> dev.yoanndev90.statusbarhider.overlay.OverlayBackground.BLACK
						else -> dev.yoanndev90.statusbarhider.overlay.OverlayBackground.SEMI
					}
				updateOverlayPrefs { copy(background = bg) }
			}
		}

		findViewById<Button>(R.id.btnShowCustomBar).setOnClickListener {
			if (!Settings.canDrawOverlays(this)) {
				Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show()
				startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
				return@setOnClickListener
			}
			val current = OverlayPrefs.load(this)
			OverlayPrefs.save(this, current.copy(enabled = true))
			StatusBarOverlayService.start(this)
			appendLog("Custom bar shown (overlay)")
		}
		findViewById<Button>(R.id.btnHideCustomBar).setOnClickListener {
			val current = OverlayPrefs.load(this)
			OverlayPrefs.save(this, current.copy(enabled = false))
			StatusBarOverlayService.stop(this)
			appendLog("Custom bar hidden")
		}

		Shizuku.addBinderReceivedListenerSticky(binderListener)
		Shizuku.addBinderDeadListener(deadListener)
		refreshStatus()
	}

	override fun onDestroy() {
		Shizuku.removeBinderReceivedListener(binderListener)
		Shizuku.removeBinderDeadListener(deadListener)
		super.onDestroy()
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == REQ_SHIZUKU) {
			appendLog(
				if (ShizukuCmd.granted()) {
					"Shizuku permission granted."
				} else {
					"Shizuku permission denied. Grant it in the Shizuku manager."
				}
			)
			refreshStatus()
		}
		if (requestCode == REQ_CALENDAR && pendingCalendarToggle) {
			pendingCalendarToggle = false
			val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
			findViewById<CheckBox>(R.id.cbCalendar).isChecked = granted
			if (granted) {
				updateOverlayPrefs { copy(showCalendar = true) }
			} else {
				Toast.makeText(this, "Calendar permission denied", Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun hasCalendarPermission(): Boolean =
		checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED

	private fun refreshStatus() {
		val text =
			when {
				!Shizuku.pingBinder() -> "Shizuku: not running"
				!ShizukuCmd.granted() -> "Shizuku: waiting for authorization"
				else -> "Shizuku: ready"
			}
		runOnUiThread { statusView.text = text }
	}

	private fun runAsync(
		label: String,
		block: () -> Unit
	) {
		appendLog("... $label")
		Thread {
			try {
				block()
			} catch (e: Exception) {
				appendLog("ERROR: ${e.message}")
			}
		}.start()
	}

	private fun appendLog(line: String) {
		runOnUiThread { logView.append(line + "\n") }
	}

	private fun bindOverlayCheck(
		id: Int,
		checked: Boolean,
		update: OverlayPrefs.(Boolean) -> OverlayPrefs
	) {
		findViewById<CheckBox>(id).apply {
			isChecked = checked
			setOnCheckedChangeListener { _, isChecked ->
				updateOverlayPrefs { update(isChecked) }
			}
		}
	}

	private fun updateOverlayPrefs(update: OverlayPrefs.() -> OverlayPrefs) {
		val updated = OverlayPrefs.load(this).update()
		OverlayPrefs.save(this, updated)
		if (updated.enabled) StatusBarOverlayService.start(this)
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
					if (fromUser) updateOverlayPrefs { update(progress) }
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
					if (fromUser) updateOverlayPrefs { update(progress + min) }
				}

				override fun onStartTrackingTouch(s: SeekBar) = Unit

				override fun onStopTrackingTouch(s: SeekBar) = Unit
			}
		)
	}

	private fun refreshOrderList() {
		val container = findViewById<LinearLayout>(R.id.orderList)
		container.removeAllViews()
		val order = OverlayPrefs.load(this).widgetOrder.toMutableList()
		order.forEachIndexed { index, id ->
			val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
			val label =
				TextView(this).apply {
					text = "${index + 1}. ${dev.yoanndev90.statusbarhider.overlay.WidgetId.label(id)}"
					textSize = 14f
					layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
				}
			val up =
				Button(this).apply {
					text = "↑"
					setOnClickListener {
						if (index > 0) {
							val o = OverlayPrefs.load(this@MainActivity).widgetOrder.toMutableList()
							val tmp = o[index - 1]
							o[index - 1] = o[index]
							o[index] = tmp
							updateOverlayPrefs { copy(widgetOrder = o) }
							refreshOrderList()
						}
					}
				}
			val down =
				Button(this).apply {
					text = "↓"
					setOnClickListener {
						val o = OverlayPrefs.load(this@MainActivity).widgetOrder.toMutableList()
						if (index < o.size - 1) {
							val tmp = o[index + 1]
							o[index + 1] = o[index]
							o[index] = tmp
							updateOverlayPrefs { copy(widgetOrder = o) }
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

	private fun requireGranted(): Boolean {
		if (!ShizukuCmd.granted()) {
			appendLog("Shizuku not authorized. Use button 1 first.")
			return false
		}
		return true
	}

	private fun applyHide() {
		if (!requireGranted()) return
		for (cmd in oem.hide) {
			val (_, out) = ShizukuCmd.run(cmd.cmd)
			appendLog("${cmd.name} -> ${out.ifEmpty { "ok" }}")
		}
		appendLog("Done. Swipe-down is preserved.")
	}

	private fun showState() {
		if (!requireGranted()) return
		for (cmd in oem.status) {
			val (_, out) = ShizukuCmd.run(cmd.cmd)
			appendLog("${cmd.name} = ${out.ifEmpty { "(empty)" }}")
		}
	}

	private fun restore() {
		if (!requireGranted()) return
		for (cmd in oem.restore) {
			ShizukuCmd.run(cmd.cmd)
			appendLog("${cmd.name} -> restored")
		}
		runOnUiThread {
			val current = OverlayPrefs.load(this)
			OverlayPrefs.save(this, current.copy(enabled = false))
			StatusBarOverlayService.stop(this)
		}
	}
}
