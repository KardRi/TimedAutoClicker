package com.autoclicker.timed.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autoclicker.timed.R
import com.autoclicker.timed.model.ClickTask
import com.autoclicker.timed.ui.MainActivity
import com.autoclicker.timed.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduledClickService : Service() {

    companion object {
        private const val TAG = "ScheduledClickService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "timed_click_channel"

        const val ACTION_START_TASK = "com.autoclicker.timed.START_TASK"
        const val ACTION_CANCEL_TASK = "com.autoclicker.timed.CANCEL_TASK"
        const val ACTION_TASK_LOG = "com.autoclicker.timed.TASK_LOG"
        const val ACTION_TASK_FINISHED = "com.autoclicker.timed.TASK_FINISHED"

        const val EXTRA_TASK = "extra_task"
        const val EXTRA_LOG_MESSAGE = "extra_log_message"

        var isTaskRunning = false
            private set
        var currentTask: ClickTask? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var timingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationManager: NotificationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimedAutoClicker::TaskWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TASK -> {
                val task = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_TASK, ClickTask::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_TASK) as? ClickTask
                }

                if (task != null) {
                    startScheduledTask(task)
                } else {
                    stopSelf()
                }
            }
            ACTION_CANCEL_TASK -> {
                cancelScheduledTask("使用者主動取消任務")
            }
        }
        return START_NOT_STICKY
    }

    private fun startScheduledTask(task: ClickTask) {
        timingJob?.cancel()
        currentTask = task
        isTaskRunning = true

        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes max */)

        startForeground(NOTIFICATION_ID, buildNotification("待命中...", task))
        sendLog("任務已啟動！目標時間：${TimeUtils.formatFullDateTime(task.targetTimeMillis)}，座標：(${task.x.toInt()}, ${task.y.toInt()})")

        timingJob = serviceScope.launch {
            val target = task.targetTimeMillis
            var now = System.currentTimeMillis()

            if (target <= now) {
                sendLog("注意：目標時間已過或為即刻執行，立即觸發點擊！")
                triggerClick(task)
                return@launch
            }

            // Coarse delay phase: sleep until 3 seconds before target
            val sleepUntilCoarse = target - 3000L
            while (isActive && System.currentTimeMillis() < sleepUntilCoarse) {
                val remaining = target - System.currentTimeMillis()
                updateNotification("倒數剩餘：${TimeUtils.formatRemaining(remaining)}", task)
                delay(500L)
            }

            // Fine delay phase: sleep until 50ms before target
            val sleepUntilFine = target - 50L
            while (isActive && System.currentTimeMillis() < sleepUntilFine) {
                delay(10L)
            }

            // Ultra-precise spin-wait phase for millisecond precision
            while (isActive && System.currentTimeMillis() < target) {
                if (target - System.currentTimeMillis() > 2) {
                    Thread.sleep(1)
                } else {
                    Thread.yield()
                }
            }

            if (isActive) {
                val triggerActualTime = System.currentTimeMillis()
                val delta = triggerActualTime - target
                sendLog("抵達觸發時間！誤差: ${delta}ms (系統時間: ${TimeUtils.formatFullDateTime(triggerActualTime)})")
                triggerClick(task)
            }
        }
    }

    private suspend fun triggerClick(task: ClickTask) {
        val service = AutoClickAccessibilityService.instance
        if (service == null) {
            val err = "錯誤：無障礙服務未連線！無法執行點擊。請確認已在系統設定中開啟無障礙服務。"
            Log.e(TAG, err)
            sendLog(err)
            finishTask()
            return
        }

        withContext(Dispatchers.Main) {
            updateNotification("正在執行點擊中...", task)
        }

        service.performMultipleTaps(
            x = task.x,
            y = task.y,
            repeatCount = task.repeatCount,
            intervalMs = task.repeatIntervalMs,
            durationMs = task.durationMs,
            logCallback = { msg ->
                sendLog(msg)
            }
        )

        withContext(Dispatchers.Main) {
            updateNotification("點擊已完成！", task)
        }
        delay(1000L)
        finishTask()
    }

    private fun cancelScheduledTask(reason: String) {
        timingJob?.cancel()
        timingJob = null
        sendLog(reason)
        finishTask()
    }

    private fun finishTask() {
        isTaskRunning = false
        currentTask = null
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        sendBroadcast(Intent(ACTION_TASK_FINISHED).apply {
            setPackage(packageName)
        })
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sendLog(message: String) {
        val formattedLog = "[${TimeUtils.formatTime(System.currentTimeMillis())}] $message"
        Log.i(TAG, formattedLog)
        val intent = Intent(ACTION_TASK_LOG).apply {
            putExtra(EXTRA_LOG_MESSAGE, formattedLog)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String, task: ClickTask): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, ScheduledClickService::class.java).apply {
            action = ACTION_CANCEL_TASK
        }
        val pendingCancelIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$statusText\n目標: ${TimeUtils.formatFullDateTime(task.targetTimeMillis)}\n座標: (${task.x.toInt()}, ${task.y.toInt()})")
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpenIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消任務", pendingCancelIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String, task: ClickTask) {
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(statusText, task))
    }

    override fun onDestroy() {
        timingJob?.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        isTaskRunning = false
        currentTask = null
        super.onDestroy()
    }
}
