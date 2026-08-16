package com.example.aiassistant

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator

class HudOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var hudView: ArcReactorView? = null
    private var isShowing = false

    fun showListeningHud(persona: AssistantPersona) {
        if (isShowing) return

        val layoutParams = WindowManager.LayoutParams(
            220,
            220,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        hudView = ArcReactorView(context, persona)
        try {
            windowManager.addView(hudView, layoutParams)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideHud() {
        if (!isShowing || hudView == null) return
        try {
            hudView?.stopAnimation()
            windowManager.removeView(hudView)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            hudView = null
            isShowing = false
        }
    }

    @SuppressLint("ViewConstructor")
    private class ArcReactorView(context: Context, private val persona: AssistantPersona) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var rotationAngle = 0f
        private var pulseRadius = 30f
        private var animator: ValueAnimator? = null

        init {
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 2000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    rotationAngle = it.animatedValue as Float
                    pulseRadius = 25f + (kotlin.math.sin(Math.toRadians(rotationAngle.toDouble())).toFloat() * 8f)
                    invalidate()
                }
                start()
            }
        }

        fun stopAnimation() {
            animator?.cancel()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f

            // JARVIS = Cyan/Stark Blue (#00E5FF), FRIDAY = Tactical Amber/Crimson (#FF3D00)
            val baseColor = if (persona == AssistantPersona.JARVIS) Color.parseColor("#00E5FF") else Color.parseColor("#FF5722")

            // Outer Glowing Ring
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = baseColor
            paint.alpha = 180
            canvas.drawCircle(cx, cy, 60f, paint)

            // Rotating Segmented Rings
            canvas.save()
            canvas.rotate(rotationAngle, cx, cy)
            paint.strokeWidth = 6f
            paint.alpha = 255
            val rect = RectF(cx - 50f, cy - 50f, cx + 50f, cy + 50f)
            canvas.drawArc(rect, 0f, 60f, false, paint)
            canvas.drawArc(rect, 120f, 60f, false, paint)
            canvas.drawArc(rect, 240f, 60f, false, paint)
            canvas.restore()

            // Counter-rotating Inner Core
            canvas.save()
            canvas.rotate(-rotationAngle * 1.5f, cx, cy)
            paint.strokeWidth = 3f
            paint.alpha = 140
            val innerRect = RectF(cx - 35f, cy - 35f, cx + 35f, cy + 35f)
            canvas.drawArc(innerRect, 30f, 40f, false, paint)
            canvas.drawArc(innerRect, 150f, 40f, false, paint)
            canvas.drawArc(innerRect, 270f, 40f, false, paint)
            canvas.restore()

            // Glowing Pulsing Center Core
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = 220
            canvas.drawCircle(cx, cy, pulseRadius, paint)
        }
    }
}
