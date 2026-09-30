package dev.yoanndev90.statusbarhider

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class AppViewModel(
	application: Application
) : AndroidViewModel(application) {
	private val _statusText = MutableStateFlow("")
	val statusText = _statusText.asStateFlow()

	fun setStatus(status: String) {
		_statusText.value = status
	}

	private val _logs: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
	val logs = _logs.asStateFlow()

	private val _oem: MutableStateFlow<OemConfig> = MutableStateFlow(OemConfig.default)
	val oem = _oem.asStateFlow()

	init {
		val oemId = OemConfig.detect(application)
		OemConfig.saveId(application, oemId)
		_oem.value = OemConfig.load(application, oemId)
	}

	fun refreshStatus() {
		val text =
			when {
				!Shizuku.pingBinder() -> "Shizuku: not running"
				!ShizukuCmd.granted() -> "Shizuku: waiting for authorization"
				else -> "Shizuku: ready"
			}
		_statusText.value = text
	}

	fun runAsync(
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

	fun appendLog(line: String) {
		_logs.value += line
	}

	private fun requireGranted(): Boolean {
		if (!ShizukuCmd.granted()) {
			appendLog("Shizuku not authorized. Use button 1 first.")
			return false
		}
		return true
	}

	fun applyHide() {
		if (!requireGranted()) return
		for (cmd in _oem.value.hide) {
			val (_, out) = ShizukuCmd.run(cmd.cmd)
			appendLog("${cmd.name} -> ${out.ifEmpty { "ok" }}")
		}
		appendLog("Done. Swipe-down is preserved.")
	}

	fun showState() {
		if (!requireGranted()) return
		for (cmd in _oem.value.status) {
			val (_, out) = ShizukuCmd.run(cmd.cmd)
			appendLog("${cmd.name} = ${out.ifEmpty { "(empty)" }}")
		}
	}

	fun restore() {
		if (!requireGranted()) return
		for (cmd in _oem.value.restore) {
			ShizukuCmd.run(cmd.cmd)
			appendLog("${cmd.name} -> restored")
		}
	}
}
