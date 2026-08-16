package com.example.aiassistant

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator

class HudOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var hudView: ArcReactorView? = null
    private var isShowing = false

    fun showListeningHud(persona: AssistantPersona) {
        if (isShowing || !Settings.canDrawOverlays(context)) return

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

        try {
            hudView = ArcReactorView(context, persona)
            windowManager.addView(hudView, layoutParams)
            isShowing = true
        } catch (e: Exception) {
            hudView = null
            isShowing = false
        }
    }

    fun hideHud() {
        if (!isShowing || hudView == null) return
        try {
            hudView?.stopAnimation()
            windowManager.removeView(hudView)
        } catch (_: Exception) {
        } finally {
            hudView = null
            isShowing = false
        }
    }

    @SuppressLint("ViewConstructor")
    private class ArcReactorView(context: Context, private val persona: AssistantPersona) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var rotationAngle = 0f
        private var pulseRadius = 28f
        private var animator: ValueAnimator? = null

        init {
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 2000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    rotationAngle = it.animatedValue as Float
                    pulseRadius = 24f + (kotlin.math.sin(Math.toRadians(rotationAngle.toDouble())).toFloat() * 6f)
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

            val baseColor = if (persona == AssistantPersona.JARVIS) Color.parseColor("#00E5FF") else Color.parseColor("#FF5722")

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = baseColor
            paint.alpha = 180
            canvas.drawCircle(cx, cy, 60f, paint)

            canvas.save()
            canvas.rotate(rotationAngle, cx, cy)
            paint.strokeWidth = 6f
            paint.alpha = 255
            val rect = RectF(cx - 50f, cy - 50f, cx + 50f, cy + 50f)
            canvas.drawArc(rect, 0f, 60f, false, paint)
            canvas.drawArc(rect, 120f, 60f, false, paint)
            canvas.drawArc(rect, 240f, 60f, false, paint)
            canvas.restore()

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = 220
            canvas.drawCircle(cx, cy, pulseRadius, paint)
        }
    }
}
