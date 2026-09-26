package dev.yoanndev90.statusbarhider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import rikka.shizuku.Shizuku

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "StatusBarHider"
        private const val OEM_ID = "hyperos"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "Boot completed — scheduling status bar hide")

        val listener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                Shizuku.removeBinderReceivedListener(this)
                if (!ShizukuCmd.granted()) {
                    Log.w(TAG, "Shizuku not authorized — skipping auto-hide")
                    return
                }
                Log.i(TAG, "Shizuku ready — applying hide")
                val oem = OemConfig.load(context, OEM_ID)
                for (cmd in oem.hide) {
                    val r = ShizukuCmd.run(cmd.cmd)
                    Log.i(TAG, "${cmd.name} -> exit=${r.exit} ${r.out}")
                }
                Log.i(TAG, "Auto-hide done")
            }
        }
        Shizuku.addBinderReceivedListener(listener)
    }
}
