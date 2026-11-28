package com.harmanibike.voiceo3

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact animated circular audio spectrum for Tata's voice screen.
 * Responds to SpeechRecognizer RMS levels; no microphone stream is opened here.
 */
class CircleSpectrumView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var amplitude = 0f
    private var active = false
    private var phase = 0f
    private val purple = Color.rgb(112, 63, 238)
    private val violet = Color.rgb(180, 91, 255)
    private val cyan = Color.rgb(90, 220, 235)

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
    }

    fun setAmplitude(value: Float) {
        amplitude = value.coerceIn(0f, 1f)
        if (active) invalidate()
    }

    fun setActive(value: Boolean) {
        active = value
        if (!active) amplitude = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = (width.coerceAtMost(height) * 0.29f)
        phase += if (active) 0.10f else 0.025f
        val pulse = if (active) amplitude else 0.08f + 0.035f * (1f + sin(phase * 0.7f))
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(if (active) 38 else 20, 137, 86, 245)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, base * (2.0f + pulse * 0.22f), halo)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color = Color.argb(95, 137, 105, 245)
        canvas.drawCircle(cx, cy, base * 1.72f, paint)
        paint.strokeWidth = 1.6f
        paint.color = Color.argb(75, 104, 190, 255)
        canvas.drawCircle(cx, cy, base * 1.48f, paint)

        val bars = 64
        for (i in 0 until bars) {
            val angle = (2.0 * PI * i / bars - PI / 2.0).toFloat()
            val wave = (0.5f + 0.5f * sin(i * 0.57f + phase * 2.2f))
            val detail = (0.5f + 0.5f * cos(i * 0.31f - phase * 1.4f))
            val energy = if (active) (0.12f + amplitude * (0.25f + 0.75f * wave) + 0.12f * detail)
                         else (0.10f + 0.10f * wave)
            val inner = base * 1.12f
            val outer = inner + base * energy.coerceIn(0.08f, 1.05f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (i % 4 == 0) 4.2f else 2.7f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = when {
                i % 3 == 0 -> violet
                i % 3 == 1 -> purple
                else -> cyan
            }
            canvas.drawLine(
                cx + cos(angle) * inner, cy + sin(angle) * inner,
                cx + cos(angle) * outer, cy + sin(angle) * outer, paint
            )
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(65, 43, 164)
        canvas.drawCircle(cx, cy, base * 1.02f, paint)
        paint.color = Color.rgb(255, 255, 255)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = base * 0.40f
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        canvas.drawText("TATA", cx, cy + base * 0.14f, paint)
        paint.textSize = base * 0.18f
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.color = Color.rgb(218, 210, 255)
        canvas.drawText(if (active) "LISTENING" else "VOICE", cx, cy + base * 0.48f, paint)

        if (active || isAttachedToWindow) postInvalidateDelayed(if (active) 32L else 80L)
    }

    override fun onDetachedFromWindow() {
        active = false
        super.onDetachedFromWindow()
    }
}
