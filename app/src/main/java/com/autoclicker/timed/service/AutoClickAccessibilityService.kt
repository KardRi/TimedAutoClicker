package com.autoclicker.timed.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.delay

class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickService"
        var instance: AutoClickAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        const val ACTION_SERVICE_STATE_CHANGED = "com.autoclicker.timed.SERVICE_STATE_CHANGED"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AutoClickAccessibilityService Connected")
        sendBroadcast(Intent(ACTION_SERVICE_STATE_CHANGED).apply {
            putExtra("connected", true)
            setPackage(packageName)
        })
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        sendBroadcast(Intent(ACTION_SERVICE_STATE_CHANGED).apply {
            putExtra("connected", false)
            setPackage(packageName)
        })
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not required for active gesture injection
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoClickAccessibilityService Interrupted")
    }

    /**
     * Dispatch a single tap at coordinates (x, y) with specified press duration.
     */
    fun performTap(
        x: Float,
        y: Float,
        durationMs: Long = 50L,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Gesture completed successfully at ($x, $y)")
                onComplete?.invoke(true, "點擊完成 座標: (${x.toInt()}, ${y.toInt()})")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Gesture cancelled at ($x, $y)")
                onComplete?.invoke(false, "點擊被系統取消 座標: (${x.toInt()}, ${y.toInt()})")
            }
        }, null)

        if (!dispatched) {
            val msg = "無法派發手勢 (dispatchGesture 返回 false，螢幕可能處於關閉或受保護狀態)"
            Log.e(TAG, msg)
            onComplete?.invoke(false, msg)
        }
    }

    /**
     * Perform burst clicks with interval.
     */
    suspend fun performMultipleTaps(
        x: Float,
        y: Float,
        repeatCount: Int,
        intervalMs: Long,
        durationMs: Long,
        logCallback: ((String) -> Unit)? = null
    ) {
        val total = repeatCount.coerceAtLeast(1)
        for (i in 1..total) {
            logCallback?.invoke("正在執行第 $i/$total 次點擊...")
            performTap(x, y, durationMs) { success, msg ->
                logCallback?.invoke("第 $i 次: $msg")
            }
            if (i < total && intervalMs > 0) {
                delay(intervalMs)
            }
        }
    }
}
