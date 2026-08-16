package com.example.aiassistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class ScriptSandboxEngine(private val context: Context) {

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        mainHandler.post {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
            }
        }
    }

    suspend fun executeScript(jsCode: String): String = suspendCancellableCoroutine { continuation ->
        mainHandler.post {
            if (webView == null) {
                continuation.resume("Error: Sandbox uninitialized.")
                return@post
            }

            // Wrap in safe IIFE closure to return result
            val wrappedCode = """
                (function() {
                    try {
                        $jsCode
                    } catch (err) {
                        return "Runtime Error: " + err.message;
                    }
                })();
            """.trimIndent()

            webView?.evaluateJavascript(wrappedCode) { result ->
                val cleanResult = result?.replace("^\"|\"$".toRegex(), "")?.replace("\\\"", "\"")
                continuation.resume(cleanResult ?: "null")
            }
        }
    }
}
