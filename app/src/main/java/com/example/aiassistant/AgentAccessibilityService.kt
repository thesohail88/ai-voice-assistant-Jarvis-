package com.example.aiassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenElement(
    val text: String,
    val viewId: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val bounds: Rect
)

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AgentAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun triggerGlobalAction(actionId: Int): Boolean {
        return performGlobalAction(actionId)
    }

    /**
     * Inspects the active screen and extracts visible UI interactive nodes.
     */
    fun captureCurrentScreenElements(): List<ScreenElement> {
        val elements = mutableListOf<ScreenElement>()
        val rootNode = rootInActiveWindow ?: return elements

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            val viewId = node.viewIdResourceName ?: ""
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (text.isNotBlank() || node.isClickable || node.isEditable) {
                elements.add(
                    ScreenElement(
                        text = text.trim(),
                        viewId = viewId,
                        isClickable = node.isClickable,
                        isEditable = node.isEditable,
                        bounds = bounds
                    )
                )
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(rootNode)
        return elements
    }

    fun getScreenContextSummary(): String {
        val elements = captureCurrentScreenElements()
        if (elements.isEmpty()) return "Screen empty or permission pending."

        return elements
            .filter { it.text.isNotBlank() }
            .take(25)
            .joinToString("; ") { "[Text: '${it.text}', Clickable: ${it.isClickable}, Editable: ${it.isEditable}]" }
    }

    fun clickElementByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                parent = parent.parent
            }
        }
        return false
    }

    fun typeTextIntoFocusedNode(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootNode.findAccessibilityNodeInfosByText("").firstOrNull { it.isEditable }
            ?: return false

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun performScroll(forward: Boolean): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return rootNode.performAction(action)
    }
}
