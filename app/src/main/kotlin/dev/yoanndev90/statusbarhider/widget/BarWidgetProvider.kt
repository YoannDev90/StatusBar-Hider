package dev.yoanndev90.statusbarhider.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.control.ControlActions
import dev.yoanndev90.statusbarhider.control.ControlAuth
import dev.yoanndev90.statusbarhider.data.AppSettings
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.hide.HideController

/**
 * Home screen widget: two buttons, one per state the labels mirror.
 *
 * Taps dispatch the same [ControlActions] as the external API, and every
 * state change goes through [OverlayController] / [HideController], which
 * call [updateAll] - so the labels never go stale.
 */
class BarWidgetProvider : AppWidgetProvider() {
	companion object {
		/** Rebuilds every placed instance; call after a state change the labels show. */
		fun updateAll(context: Context) {
			val app = context.applicationContext
			val am = AppWidgetManager.getInstance(app) ?: return
			val ids = am.getAppWidgetIds(ComponentName(app, BarWidgetProvider::class.java))
			if (ids.isEmpty()) return
			val views = buildViews(app)
			for (id in ids) {
				am.updateAppWidget(id, views)
			}
		}

		private fun buildViews(app: Context): RemoteViews {
			val barOn = OverlayPrefsRepository
				.getInstance(app)
				.state.value.enabled
			val systemHidden = HideController.isHidden(app)
			return RemoteViews(app.packageName, R.layout.widget_bar).apply {
				setTextViewText(
					R.id.widget_action_bar,
					app.getString(if (barOn) R.string.action_hide_custom_bar else R.string.action_show_custom_bar)
				)
				setTextViewText(
					R.id.widget_action_system,
					app.getString(if (systemHidden) R.string.action_restore else R.string.action_hide_status_bar)
				)
				setOnClickPendingIntent(R.id.widget_action_bar, broadcast(app, ControlActions.ACTION_TOGGLE_BAR, 1))
				setOnClickPendingIntent(
					R.id.widget_action_system,
					broadcast(app, ControlActions.ACTION_TOGGLE_SYSTEM_BAR, 2)
				)
			}
		}

		private fun broadcast(
			app: Context,
			action: String,
			requestCode: Int
		): PendingIntent =
			PendingIntent.getBroadcast(
				app,
				requestCode,
				Intent(app, BarWidgetProvider::class.java)
					.setAction(action)
					.putExtra(ControlAuth.EXTRA_TOKEN, AppSettings.controlToken(app)),
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
			)
	}

	override fun onReceive(
		context: Context,
		intent: Intent
	) {
		// Custom tap actions are handled here; everything else (including
		// APPWIDGET_UPDATE, which the system sends) goes through the default
		// provider path. Control actions run through the same ControlAuth gate
		// as the external API, and broadcast() adds the token the gate wants.
		if (ControlActions.isControlAction(intent.action)) {
			if (ControlAuth.accept(context, intent)) {
				ControlActions.dispatch(context, intent.action)
			}
			return
		}
		super.onReceive(context, intent)
	}

	override fun onUpdate(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray
	) {
		updateAll(context)
	}
}
