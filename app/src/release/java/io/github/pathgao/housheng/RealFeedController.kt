package io.github.pathgao.housheng

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

internal class RealFeedController(service: FeedService) {
    fun observe(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean = false
    fun clear() = Unit
    fun close() = Unit
}
