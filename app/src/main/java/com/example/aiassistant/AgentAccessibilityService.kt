package com.example.aiassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AgentAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AgentAccessibility", "Stark Agent Accessibility Service connected & active.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Active tree monitoring is queried on-demand by the AI router
    }

    override fun onInterrupt() {
        Log.w("AgentAccessibility", "Accessibility service interrupted.")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ==========================================
    // 1. SCREEN HIERARCHY & CONTEXT EXTRACTION
    // ==========================================

    fun getScreenContextSummary(): String {
        val root = rootInActiveWindow ?: return "Screen tree unavailable or device locked."
        val builder = StringBuilder()
        traverseNodeTree(root, builder, 0)
        return if (builder.isBlank()) "Empty screen state." else builder.toString().trim()
    }

    private fun traverseNodeTree(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (node == null || depth > 8) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val viewId = node.viewIdResourceName?.substringAfterLast("/")
        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val isScrollable = node.isScrollable

        val label = when {
            !text.isNullOrBlank() -> text
            !desc.isNullOrBlank() -> desc
            else -> ""
        }

        if (label.isNotBlank() || isEditable || isClickable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val type = when {
                isEditable -> "[Input]"
                isClickable -> "[Button]"
                isScrollable -> "[Scrollable]"
                else -> "[Text]"
            }
            val idTag = if (!viewId.isNullOrBlank()) " (#$viewId)" else ""
            out.append("$type '$label'$idTag at (${bounds.centerX()},${bounds.centerY()})\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseNodeTree(child, out, depth + 1)
            child?.recycle()
        }
    }

    // ==========================================
    // 2. ELEMENT TARGETING & CLICK ACTIONS
    // ==========================================

    fun clickElementByText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matchedNodes = root.findAccessibilityNodeInfosByText(targetText)
        if (!matchedNodes.isNullOrEmpty()) {
            for (node in matchedNodes) {
                if (performClickOnNodeOrParent(node)) {
                    recycleNodeList(matchedNodes)
                    return true
                }
            }
            recycleNodeList(matchedNodes)
        }

        // Fallback: Traversal search for partial or contentDescription match
        return findAndClickNodeFuzzy(root, targetText.lowercase())
    }

    private fun findAndClickNodeFuzzy(node: AccessibilityNodeInfo?, query: String): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (text.contains(query) || desc.contains(query)) {
            if (performClickOnNodeOrParent(node)) return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (findAndClickNodeFuzzy(child, query)) {
                child?.recycle()
                return true
            }
            child?.recycle()
        }
        return false
    }

    fun clickElementById(viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (!nodes.isNullOrEmpty()) {
            for (node in nodes) {
                if (performClickOnNodeOrParent(node)) {
                    recycleNodeList(nodes)
                    return true
                }
            }
            recycleNodeList(nodes)
        }
        return false
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    // ==========================================
    // 3. TEXT INPUT & FIELD MANIPULATION
    // ==========================================

    fun typeTextIntoFocusedField(textToType: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focusedNode.recycle()
            return result
        }

        // Fallback: search for the first available editable node
        return findAndSetTextOnFirstInput(root, textToType)
    }

    private fun findAndSetTextOnFirstInput(node: AccessibilityNodeInfo?, textToType: String): Boolean {
        if (node == null) return false

        if (node.isEditable) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (findAndSetTextOnFirstInput(child, textToType)) {
                child?.recycle()
                return true
            }
            child?.recycle()
        }
        return false
    }

    fun clearFocusedField(): Boolean {
        return typeTextIntoFocusedField("")
    }

    // ==========================================
    // 4. COORDINATE GESTURES & SWIPES
    // ==========================================

    suspend fun clickAtCoordinates(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGestureAsync(gesture)
    }

    suspend fun longPressAtCoordinates(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 800))
            .build()
        return dispatchGestureAsync(gesture)
    }

    suspend fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGestureAsync(gesture)
    }

    suspend fun scrollDown(): Boolean {
        val displayMetrics = resources.displayMetrics
        val cx = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.75f
        val endY = displayMetrics.heightPixels * 0.25f
        return swipe(cx, startY, cx, endY, 350)
    }

    suspend fun scrollUp(): Boolean {
        val displayMetrics = resources.displayMetrics
        val cx = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.25f
        val endY = displayMetrics.heightPixels * 0.75f
        return swipe(cx, startY, cx, endY, 350)
    }

    suspend fun swipeRight(): Boolean {
        val displayMetrics = resources.displayMetrics
        val cy = displayMetrics.heightPixels / 2f
        val startX = displayMetrics.widthPixels * 0.15f
        val endX = displayMetrics.widthPixels * 0.85f
        return swipe(startX, cy, endX, cy, 300)
    }

    suspend fun swipeLeft(): Boolean {
        val displayMetrics = resources.displayMetrics
        val cy = displayMetrics.heightPixels / 2f
        val startX = displayMetrics.widthPixels * 0.85f
        val endX = displayMetrics.widthPixels * 0.15f
        return swipe(startX, cy, endX, cy, 300)
    }

    private suspend fun dispatchGestureAsync(gesture: GestureDescription): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                deferred.complete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                deferred.complete(false)
            }
        }
        val dispatched = dispatchGesture(gesture, callback, null)
        return if (dispatched) deferred.await() else false
    }

    // ==========================================
    // 5. GLOBAL ANDROID SYSTEM ACTIONS
    // ==========================================

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun openPowerDialog(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

    fun lockDeviceScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    fun takeScreenSnapshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }

    private fun recycleNodeList(nodes: List<AccessibilityNodeInfo>) {
        for (node in nodes) {
            try {
                node.recycle()
            } catch (_: Exception) {}
        }
    }
}
