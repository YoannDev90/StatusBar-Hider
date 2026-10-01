package dev.yoanndev90.statusbarhider.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Battery level silhouette: outline + proportional fill + bolt when charging.
 * Color is set from overlay prefs (light/dark text color).
 */
class BatteryView
	@JvmOverloads
	constructor(
		context: Context,
		attrs: AttributeSet? = null
	) : View(context, attrs) {
		var level: Int = 100
			set(value) {
				field = value.coerceIn(0, 100)
				invalidate()
			}
		var charging: Boolean = false
			set(value) {
				field = value
				invalidate()
			}
		var fgColor: Int = 0xFFFFFFFF.toInt()
			set(value) {
				field = value
				paint.color = value
				invalidate()
			}

		private val paint =
			Paint(Paint.ANTI_ALIAS_FLAG).apply {
				style = Paint.Style.STROKE
				strokeWidth = 3f
				color = fgColor
			}
		private val fillPaint =
			Paint(Paint.ANTI_ALIAS_FLAG).apply {
				style = Paint.Style.FILL
				color = fgColor
			}

		override fun onMeasure(
			widthMeasureSpec: Int,
			heightMeasureSpec: Int
		) {
			val d = resources.displayMetrics.density
			setMeasuredDimension((22 * d).toInt(), (12 * d).toInt())
		}

		override fun onDraw(canvas: Canvas) {
			super.onDraw(canvas)
			val w = measuredWidth.toFloat()
			val h = measuredHeight.toFloat()
			if (w <= 0 || h <= 0) return
			val tipW = w * 0.08f
			val bodyR = android.graphics.RectF(1.5f, 1.5f, w - tipW - 1f, h - 1.5f)
			fillPaint.color = fgColor
			paint.color = fgColor
			canvas.drawRoundRect(bodyR, 3f, 3f, paint)
			canvas.drawRect(w - tipW, h * 0.32f, w - 1f, h * 0.68f, fillPaint)
			val frac = level / 100f
			if (frac > 0f) {
				val pad = 4f
				val fillR =
					android.graphics.RectF(
						bodyR.left + pad,
						bodyR.top + pad,
						bodyR.left + pad + (bodyR.width() - pad * 2) * frac,
						bodyR.bottom - pad
					)
				canvas.drawRect(fillR, fillPaint)
			}
			if (charging) {
				val cx = bodyR.centerX()
				val cy = bodyR.centerY()
				val bolt =
					android.graphics.Path().apply {
						moveTo(cx + 3f, cy - 6f)
						lineTo(cx - 3f, cy + 1f)
						lineTo(cx, cy + 1f)
						lineTo(cx - 3f, cy + 6f)
						lineTo(cx + 3f, cy - 1f)
						lineTo(cx, cy - 1f)
						close()
					}
				canvas.drawPath(bolt, fillPaint)
			}
		}
	}
