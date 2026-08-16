package com.example.aiassistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class ArcReactorHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rotationAngle = 0f
    private var pulseRadius = 32f
    private var animator: ValueAnimator? = null
    private var activePersona = AssistantPersona.JARVIS
    private var isCoreActive = false

    init {
        startCoreAnimation()
    }

    fun setPersona(persona: AssistantPersona) {
        this.activePersona = persona
        invalidate()
    }

    fun setCoreState(active: Boolean) {
        this.isCoreActive = active
        invalidate()
    }

    private fun startCoreAnimation() {
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                pulseRadius = 26f + (sin(Math.toRadians(rotationAngle.toDouble())).toFloat() * 7f)
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        val primaryColor = if (activePersona == AssistantPersona.JARVIS) {
            Color.parseColor("#00E5FF")
        } else {
            Color.parseColor("#FF5722")
        }

        // 1. Outer Concentric Halo
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = primaryColor
        paint.alpha = if (isCoreActive) 160 else 70
        canvas.drawCircle(cx, cy, 80f, paint)

        // 2. Primary Segmented Shield (Clockwise)
        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)
        paint.strokeWidth = 5f
        paint.alpha = 240
        val outerRect = RectF(cx - 70f, cy - 70f, cx + 70f, cy + 70f)
        canvas.drawArc(outerRect, 0f, 60f, false, paint)
        canvas.drawArc(outerRect, 120f, 60f, false, paint)
        canvas.drawArc(outerRect, 240f, 60f, false, paint)
        canvas.restore()

        // 3. Secondary Tactical Shield (Counter-Clockwise)
        canvas.save()
        canvas.rotate(-rotationAngle * 1.8f, cx, cy)
        paint.strokeWidth = 3.5f
        paint.alpha = 180
        val innerRect = RectF(cx - 50f, cy - 50f, cx + 50f, cy + 50f)
        canvas.drawArc(innerRect, 30f, 40f, false, paint)
        canvas.drawArc(innerRect, 150f, 40f, false, paint)
        canvas.drawArc(innerRect, 270f, 40f, false, paint)
        canvas.restore()

        // 4. Center Core
        paint.style = Paint.Style.FILL
        paint.color = primaryColor
        paint.alpha = 110
        canvas.drawCircle(cx, cy, pulseRadius + 8f, paint)

        paint.color = Color.WHITE
        paint.alpha = 255
        canvas.drawCircle(cx, cy, pulseRadius - 4f, paint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
