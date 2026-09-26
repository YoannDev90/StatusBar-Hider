package dev.yoanndev90.statusbarhider

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    companion object {
        private const val REQ_SHIZUKU = 1001
    }

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
                android.content.pm.PackageManager.DONT_KILL_APP,
            )
            Toast
                .makeText(
                    this,
                    "Hidden! Access via Settings > Apps > StatusBar Hider",
                    Toast.LENGTH_LONG,
                ).show()
        }

        findViewById<Button>(R.id.btnExportLogs).setOnClickListener {
            val clip = ClipData.newPlainText("StatusBarHider logs", logView.text)
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
            Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
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
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SHIZUKU) {
            appendLog(
                if (ShizukuCmd.granted()) {
                    "Shizuku permission granted."
                } else {
                    "Shizuku permission denied. Grant it in the Shizuku manager."
                },
            )
            refreshStatus()
        }
    }

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
        block: () -> Unit,
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
    }
}
