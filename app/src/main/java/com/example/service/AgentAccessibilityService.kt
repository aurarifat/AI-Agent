package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        _isServiceActive.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Observes foreground package and window changes
        event?.packageName?.let {
            currentForegroundPackage = it.toString()
        }
    }

    override fun onInterrupt() {
        _isServiceActive.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance?.get() == this) {
            instance = null
        }
        _isServiceActive.value = false
    }

    fun clickTarget(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByTextOrDesc(root, target)
        return if (node != null) {
            performClickOnNodeOrParent(node)
        } else {
            false
        }
    }

    fun longClickTarget(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByTextOrDesc(root, target)
        return if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        } else {
            false
        }
    }

    fun typeText(target: String?, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = if (!target.isNullOrBlank()) {
            findEditableNode(root, target) ?: findNodeByTextOrDesc(root, target)
        } else {
            findFocusedOrFirstEditableNode(root)
        }

        return if (node != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } else {
            false
        }
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (direction.equals("BACKWARD", ignoreCase = true) || direction.equals("UP", ignoreCase = true)) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        return root.performAction(action)
    }

    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun swipe(direction: String): Boolean {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        when (direction.uppercase()) {
            "UP" -> {
                startX = width / 2f
                startY = height * 0.75f
                endX = width / 2f
                endY = height * 0.25f
            }
            "DOWN" -> {
                startX = width / 2f
                startY = height * 0.25f
                endX = width / 2f
                endY = height * 0.75f
            }
            "LEFT" -> {
                startX = width * 0.8f
                startY = height / 2f
                endX = width * 0.2f
                endY = height / 2f
            }
            "RIGHT" -> {
                startX = width * 0.2f
                startY = height / 2f
                endX = width * 0.8f
                endY = height / 2f
            }
            else -> {
                startX = width / 2f
                startY = height * 0.75f
                endX = width / 2f
                endY = height * 0.25f
            }
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 400)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findNodeByTextOrDesc(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val targetLower = target.lowercase().trim()

        // Exact or contains match on text
        val nodesByText = root.findAccessibilityNodeInfosByText(target)
        if (!nodesByText.isNullOrEmpty()) {
            return nodesByText[0]
        }

        // BFS search tree for text, contentDescription, or viewId
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val text = current.text?.toString()?.lowercase() ?: ""
            val desc = current.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = current.viewIdResourceName?.lowercase() ?: ""

            if (text.contains(targetLower) || desc.contains(targetLower) || viewId.contains(targetLower)) {
                return current
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findEditableNode(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val targetLower = target.lowercase().trim()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val text = current.text?.toString()?.lowercase() ?: ""
            val desc = current.contentDescription?.toString()?.lowercase() ?: ""
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) current.hintText?.toString()?.lowercase() ?: "" else ""

            if (current.isEditable && (text.contains(targetLower) || desc.contains(targetLower) || hint.contains(targetLower))) {
                return current
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findFocusedOrFirstEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.isEditable) return current
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    companion object {
        private var instance: WeakReference<AgentAccessibilityService>? = null
        var currentForegroundPackage: String = ""
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        fun get(): AgentAccessibilityService? = instance?.get()

        fun isRunning(context: Context): Boolean {
            if (instance?.get() != null) return true
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expectedServiceName = "${context.packageName}/${AgentAccessibilityService::class.java.canonicalName}"
            val simpleServiceName = "${context.packageName}/.service.AgentAccessibilityService"
            return enabledServices.contains(expectedServiceName) || enabledServices.contains(simpleServiceName)
        }
    }
}
