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

    init {
        startCoreAnimation()
    }

    fun setPersona(persona: AssistantPersona) {
        this.activePersona = persona
        invalidate()
    }

    private fun startCoreAnimation() {
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                pulseRadius = 28f + (sin(Math.toRadians(rotationAngle.toDouble())).toFloat() * 6f)
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
            Color.parseColor("#00E5FF") // Cyan / Stark Blue
        } else {
            Color.parseColor("#FF5722") // Tactical Crimson / Amber
        }

        // 1. Subtle Outer Border Glow
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = primaryColor
        paint.alpha = 60
        canvas.drawCircle(cx, cy, 75f, paint)

        // 2. Outer Segmented Ring (Rotating Clockwise)
        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)
        paint.strokeWidth = 5f
        paint.alpha = 220
        val outerBounds = RectF(cx - 65f, cy - 65f, cx + 65f, cy + 65f)
        canvas.drawArc(outerBounds, 0f, 65f, false, paint)
        canvas.drawArc(outerBounds, 120f, 65f, false, paint)
        canvas.drawArc(outerBounds, 240f, 65f, false, paint)
        canvas.restore()

        // 3. Inner Counter-Rotating Ring
        canvas.save()
        canvas.rotate(-rotationAngle * 1.6f, cx, cy)
        paint.strokeWidth = 3f
        paint.alpha = 140
        val innerBounds = RectF(cx - 45f, cy - 45f, cx + 45f, cy + 45f)
        canvas.drawArc(innerBounds, 30f, 45f, false, paint)
        canvas.drawArc(innerBounds, 150f, 45f, false, paint)
        canvas.drawArc(innerBounds, 270f, 45f, false, paint)
        canvas.restore()

        // 4. Center Glowing Vibranium Core
        paint.style = Paint.Style.FILL
        paint.color = primaryColor
        paint.alpha = 90
        canvas.drawCircle(cx, cy, pulseRadius + 6f, paint)

        paint.color = Color.WHITE
        paint.alpha = 240
        canvas.drawCircle(cx, cy, pulseRadius - 4f, paint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
