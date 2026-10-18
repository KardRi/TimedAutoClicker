package com.autoclicker.timed.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.autoclicker.timed.R

class FloatingTargetService : Service() {

    companion object {
        const val ACTION_COORDINATES_SELECTED = "com.autoclicker.timed.COORDINATES_SELECTED"
        const val EXTRA_COORD_X = "extra_coord_x"
        const val EXTRA_COORD_Y = "extra_coord_y"
        var isShowing = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var currentTargetX = 0
    private var currentTargetY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isShowing = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this).inflate(R.layout.view_floating_target, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200
            y = 400
        }

        windowManager?.addView(floatingView, params)

        val tvCoordinates = floatingView?.findViewById<TextView>(R.id.tvCoordinates)
        val btnApply = floatingView?.findViewById<Button>(R.id.btnApplyCoords)
        val btnClose = floatingView?.findViewById<ImageButton>(R.id.btnCloseOverlay)
        val ivCrosshair = floatingView?.findViewById<ImageView>(R.id.ivCrosshair)

        fun updateTargetCoordinates() {
            floatingView?.let { view ->
                val location = IntArray(2)
                ivCrosshair?.getLocationOnScreen(location)
                // Center of the 56dp crosshair
                val crosshairWidth = ivCrosshair?.width ?: 0
                val crosshairHeight = ivCrosshair?.height ?: 0
                currentTargetX = location[0] + crosshairWidth / 2
                currentTargetY = location[1] + crosshairHeight / 2
                tvCoordinates?.text = "X: $currentTargetX, Y: $currentTargetY"
            }
        }

        floatingView?.post {
            updateTargetCoordinates()
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        ivCrosshair?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.x = initialX + (event.rawX - initialTouchX).toInt()
                    params?.y = initialY + (event.rawY - initialTouchY).toInt()
                    params?.let { windowManager?.updateViewLayout(floatingView, it) }
                    updateTargetCoordinates()
                    true
                }
                else -> false
            }
        }

        btnApply?.setOnClickListener {
            updateTargetCoordinates()
            val broadcastIntent = Intent(ACTION_COORDINATES_SELECTED).apply {
                putExtra(EXTRA_COORD_X, currentTargetX)
                putExtra(EXTRA_COORD_Y, currentTargetY)
                setPackage(packageName)
            }
            sendBroadcast(broadcastIntent)
            stopSelf()
        }

        btnClose?.setOnClickListener {
            stopSelf()
        }
    }

    override fun onDestroy() {
        isShowing = false
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
        super.onDestroy()
    }
}
