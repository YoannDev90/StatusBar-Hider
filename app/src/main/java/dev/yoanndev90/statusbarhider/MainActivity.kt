package dev.yoanndev90.statusbarhider

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    companion object {
        private const val REQ_SHIZUKU = 1001
        private const val OEM_ID = "hyperos"
    }

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var oem: OemConfig

    private val binderListener = Shizuku.OnBinderReceivedListener { refreshStatus() }
    private val deadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { statusView.text = "Shizuku: disconnected" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        oem = OemConfig.load(this, OEM_ID)

        val dp = { i: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, i.toFloat(), resources.displayMetrics
            ).toInt()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(16))
        }

        // Title
        root.addView(TextView(this).apply {
            text = "StatusBar Hider"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1a1a1a"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = oem.name
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        // Status bar
        statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#444444"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.parseColor("#f0f0f0"))
        }
        root.addView(statusView)

        fun separator() {
            root.addView(TextView(this).apply { setPadding(0, dp(12), 0, dp(6)) })
        }

        fun sectionLabel(text: String) {
            root.addView(TextView(this).apply {
                this.text = text
                textSize = 11f
                setTextColor(Color.parseColor("#999999"))
                letterSpacing = 0.12f
                setPadding(0, dp(8), 0, dp(4))
            })
        }

        fun button(label: String, accent: Boolean = false, onClick: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { onClick() }
                setPadding(dp(16), dp(12), dp(16), dp(12))
                if (accent) {
                    setBackgroundColor(Color.parseColor("#1a73e8"))
                    setTextColor(Color.WHITE)
                }
            })
        }

        // -- Shizuku --
        sectionLabel("SHIZUKU")
        button("Authorize Shizuku") {
            if (!Shizuku.pingBinder()) {
                appendLog("Shizuku is not running. Start it, then try again.")
                return@button
            }
            if (ShizukuCmd.granted()) {
                appendLog("Already authorized.")
            } else {
                Shizuku.requestPermission(REQ_SHIZUKU)
            }
        }

        // -- Status bar --
        separator()
        sectionLabel("STATUS BAR")
        button("Hide status bar", accent = true) {
            runAsync("applying hide...") { applyHide() }
        }
        button("Check state") { runAsync("reading state...") { showState() } }
        button("Restore (undo)") { runAsync("restoring...") { restore() } }

        // -- App --
        separator()
        sectionLabel("APP")
        button("Hide from launcher") {
            val cn = ComponentName(this, MainActivity::class.java)
            packageManager.setComponentEnabledSetting(
                cn,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP,
            )
            Toast.makeText(
                this,
                "Hidden! Access via Settings > Apps > StatusBar Hider",
                Toast.LENGTH_LONG,
            ).show()
        }
        button("Export logs") {
            val clip = ClipData.newPlainText("StatusBarHider logs", logView.text)
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
            Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        // -- Log --
        separator()
        sectionLabel("LOG")
        logView = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
            setTextColor(Color.parseColor("#333333"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        scrollView = ScrollView(this).apply {
            addView(logView)
            isVerticalScrollBarEnabled = true
        }
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)

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
        requestCode: Int, permissions: Array<String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SHIZUKU) {
            appendLog(
                if (ShizukuCmd.granted()) "Shizuku permission granted."
                else "Shizuku permission denied. Grant it in the Shizuku manager.",
            )
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        val text = when {
            !Shizuku.pingBinder() -> "Shizuku: not running"
            !ShizukuCmd.granted() -> "Shizuku: waiting for authorization"
            else -> "Shizuku: ready"
        }
        runOnUiThread { statusView.text = text }
    }

    private fun runAsync(label: String, block: () -> Unit) {
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
            val r = ShizukuCmd.run(cmd.cmd)
            val status = if (r.out.isNotEmpty()) r.out else "ok"
            appendLog("${cmd.name} -> $status")
        }
        appendLog("Done. Swipe-down is preserved.")
    }

    private fun showState() {
        if (!requireGranted()) return
        for (cmd in oem.status) {
            val r = ShizukuCmd.run(cmd.cmd)
            appendLog("${cmd.name} = ${r.out.ifEmpty { "(empty)" }}")
        }
    }

    private fun restore() {
        if (!requireGranted()) return
        for (cmd in oem.restore) {
            val r = ShizukuCmd.run(cmd.cmd)
            appendLog("${cmd.name} -> restored")
        }
    }
}
