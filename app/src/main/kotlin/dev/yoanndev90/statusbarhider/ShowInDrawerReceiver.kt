package dev.yoanndev90.statusbarhider

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast

class ShowInDrawerReceiver : BroadcastReceiver() {
	override fun onReceive(
		context: Context,
		intent: Intent
	) {
		if (intent.action != "dev.yoanndev90.statusbarhider.SHOW_IN_DRAWER") return
		val cn = ComponentName(context, MainActivity::class.java)
		context.packageManager.setComponentEnabledSetting(
			cn,
			PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
			PackageManager.DONT_KILL_APP
		)
		Log.i("StatusBarHider", "Launcher icon re-enabled")
		Toast.makeText(context, "Launcher icon restored", Toast.LENGTH_SHORT).show()
	}
}
