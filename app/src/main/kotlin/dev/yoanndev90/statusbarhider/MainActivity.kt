package dev.yoanndev90.statusbarhider

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
	val viewModel: AppViewModel by viewModels()

	companion object {
		private const val REQ_SHIZUKU = 1001
	}

	private val binderListener = Shizuku.OnBinderReceivedListener { viewModel.refreshStatus() }
	private val deadListener =
		Shizuku.OnBinderDeadListener {
			viewModel.setStatus("Shizuku: disconnected")
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		Shizuku.addBinderReceivedListenerSticky(binderListener)
		Shizuku.addBinderDeadListener(deadListener)

		viewModel.refreshStatus()

		setContent {
			val ctx = this

			val oem by viewModel.oem.collectAsState()

			MaterialTheme {
				Column {
					Text(
						text = "StatusBar Hider",
						style = MaterialTheme.typography.titleLarge
					)

					Text(oem.name)

					Button(
						onClick = {
							if (!Shizuku.pingBinder()) {
								viewModel.appendLog("Shizuku is not running. Start it, then try again.")
								return@Button
							}
							if (ShizukuCmd.granted()) {
								viewModel.appendLog("Already authorized.")
							} else {
								Shizuku.requestPermission(REQ_SHIZUKU)
							}
						},
						modifier = Modifier.background(Color.Blue)
					) { Text("Authorize Shizuku") }

					Button(
						onClick = { viewModel.runAsync("applying hide...") { viewModel.applyHide() } },
						modifier = Modifier.background(Color.Blue)
					) { Text("Hide status bar") }

					Button(
						onClick = { viewModel.runAsync("reading state...") { viewModel.showState() } }
					) { Text("Check state") }

					Button(
						onClick = { viewModel.runAsync("restoring...") { viewModel.restore() } }
					) { Text("Restore (undo") }

					Button(
						onClick = {
							val cn = ComponentName(ctx, MainActivity::class.java)
							packageManager.setComponentEnabledSetting(
								cn,
								android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
								android.content.pm.PackageManager.DONT_KILL_APP
							)
							Toast
								.makeText(
									ctx,
									"Hidden! Access via Settings > Apps > StatusBar Hider",
									Toast.LENGTH_LONG
								).show()
						}
					) { Text("Hide from launcher") }

					val logs by viewModel.logs.collectAsState()
					Button(
						onClick = {
							val clip = ClipData.newPlainText("StatusBarHider logs", logs.joinToString("\n"))
							getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
							Toast.makeText(ctx, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
						}
					) { Text("Export logs") }
				}
			}
		}
	}

	override fun onDestroy() {
		Shizuku.removeBinderReceivedListener(binderListener)
		Shizuku.removeBinderDeadListener(deadListener)
		super.onDestroy()
	}

	@Deprecated(
		"This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)} passing\n      in a {@link RequestMultiplePermissions} object for the {@link ActivityResultContract} and\n      handling the result in the {@link ActivityResultCallback#onActivityResult(Object) callback}."
	)
	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == REQ_SHIZUKU) {
			viewModel.appendLog(
				if (ShizukuCmd.granted()) {
					"Shizuku permission granted."
				} else {
					"Shizuku permission denied. Grant it in the Shizuku manager."
				}
			)
			viewModel.refreshStatus()
		}
	}
}
