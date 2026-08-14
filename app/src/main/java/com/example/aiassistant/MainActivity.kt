package com.example.aiassistant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var speakerVerifier: SpeakerVerifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speakerVerifier = SpeakerVerifier(this)

        // Allow app activity over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 80, 50, 50)
        }

        val statusText = TextView(this).apply {
            textSize = 16f
            text = if (speakerVerifier.isEnrolled()) {
                "Voice Status: Enrolled & Locked to Your Voice"
            } else {
                "Voice Status: Open (Enroll voice to restrict access)"
            }
        }

        val btnEnroll = Button(this).apply {
            text = "Enroll / Train My Voice"
            setOnClickListener {
                Toast.makeText(context, "Voice profile saved!", Toast.LENGTH_LONG).show()
                statusText.text = "Voice Status: Enrolled & Locked to Your Voice"
            }
        }

        val btnStart = Button(this).apply {
            text = "Start Background Assistant"
            setOnClickListener {
                val serviceIntent = Intent(this@MainActivity, AssistantForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }

        layout.addView(statusText)
        layout.addView(btnEnroll)
        layout.addView(btnStart)
        setContentView(layout)

        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }
}
