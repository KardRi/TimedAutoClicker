package com.autoclicker.timed.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autoclicker.timed.R
import com.autoclicker.timed.databinding.ActivityMainBinding
import com.autoclicker.timed.model.ClickTask
import com.autoclicker.timed.service.AutoClickAccessibilityService
import com.autoclicker.timed.service.FloatingTargetService
import com.autoclicker.timed.service.ScheduledClickService
import com.autoclicker.timed.utils.PermissionHelper
import com.autoclicker.timed.utils.TimeUtils
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainHandler = Handler(Looper.getMainLooper())

    private var selectedCalendar = Calendar.getInstance().apply {
        add(Calendar.MINUTE, 2)
        set(Calendar.MILLISECOND, 0)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Result handled
    }

    private val taskReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                FloatingTargetService.ACTION_COORDINATES_SELECTED -> {
                    val x = intent.getIntExtra(FloatingTargetService.EXTRA_COORD_X, 0)
                    val y = intent.getIntExtra(FloatingTargetService.EXTRA_COORD_Y, 0)
                    binding.etCoordX.setText(x.toString())
                    binding.etCoordY.setText(y.toString())
                    appendLog("已套用懸浮靶心座標: ($x, $y)")
                }
                ScheduledClickService.ACTION_TASK_LOG -> {
                    val message = intent.getStringExtra(ScheduledClickService.EXTRA_LOG_MESSAGE) ?: ""
                    appendLog(message)
                }
                ScheduledClickService.ACTION_TASK_FINISHED -> {
                    updateRunningState(false)
                }
                AutoClickAccessibilityService.ACTION_SERVICE_STATE_CHANGED -> {
                    refreshPermissionStatus()
                }
            }
        }
    }

    private val tickerRunnable = object : Runnable {
        override fun run() {
            updateTimeTicker()
            mainHandler.postDelayed(this, 100L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestRequiredPermissions()
        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        updateRunningState(ScheduledClickService.isTaskRunning)
        mainHandler.post(tickerRunnable)

        val filter = IntentFilter().apply {
            addAction(FloatingTargetService.ACTION_COORDINATES_SELECTED)
            addAction(ScheduledClickService.ACTION_TASK_LOG)
            addAction(ScheduledClickService.ACTION_TASK_FINISHED)
            addAction(AutoClickAccessibilityService.ACTION_SERVICE_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(taskReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(taskReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(tickerRunnable)
        try {
            unregisterReceiver(taskReceiver)
        } catch (_: Exception) {}
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        PermissionHelper.requestIgnoreBatteryOptimizations(this)
    }

    private fun setupUI() {
        updateDateButtonText()
        updateTimeButtonText()

        binding.rgTimeMode.setOnCheckedChangeListener { _, checkedId ->
            binding.layoutDateTimeMode.visibility = if (checkedId == R.id.rbModeDateTime) View.VISIBLE else View.GONE
            binding.layoutEpochMode.visibility = if (checkedId == R.id.rbModeEpoch) View.VISIBLE else View.GONE
            binding.layoutCountdownMode.visibility = if (checkedId == R.id.rbModeCountdown) View.VISIBLE else View.GONE
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateTimeTicker()
            }
        }
        binding.etEpochInput.addTextChangedListener(watcher)
        binding.etCountdownSeconds.addTextChangedListener(watcher)
        binding.etMillisec.addTextChangedListener(watcher)
    }

    private fun setupListeners() {
        binding.btnPermAccessibility.setOnClickListener {
            PermissionHelper.openAccessibilitySettings(this)
        }
        binding.btnPermOverlay.setOnClickListener {
            PermissionHelper.openOverlaySettings(this)
        }
        binding.btnPermExactAlarm.setOnClickListener {
            PermissionHelper.openExactAlarmSettings(this)
        }

        binding.btnPickCoord.setOnClickListener {
            if (!PermissionHelper.canDrawOverlays(this)) {
                Toast.makeText(this, "請先開啟「懸浮窗權限」以顯示靶心", Toast.LENGTH_SHORT).show()
                PermissionHelper.openOverlaySettings(this)
                return@setOnClickListener
            }
            startService(Intent(this, FloatingTargetService::class.java))
            Toast.makeText(this, "已開啟懸浮靶心，拖曳至目標按鈕後點擊「帶入」", Toast.LENGTH_LONG).show()
        }

        binding.btnTestClick.setOnClickListener {
            val service = AutoClickAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "無障礙服務尚未開啟，請先授權！", Toast.LENGTH_SHORT).show()
                PermissionHelper.openAccessibilitySettings(this)
                return@setOnClickListener
            }

            val x = binding.etCoordX.text.toString().toFloatOrNull() ?: 500f
            val y = binding.etCoordY.text.toString().toFloatOrNull() ?: 1000f
            val duration = binding.etPressDuration.text.toString().toLongOrNull() ?: 50L

            appendLog("正在測試點擊座標: (${x.toInt()}, ${y.toInt()})...")
            service.performTap(x, y, duration) { success, msg ->
                runOnUiThread {
                    appendLog("測試結果: $msg")
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnSelectDate.setOnClickListener {
            val year = selectedCalendar.get(Calendar.YEAR)
            val month = selectedCalendar.get(Calendar.MONTH)
            val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(this, { _, y, m, d ->
                selectedCalendar.set(Calendar.YEAR, y)
                selectedCalendar.set(Calendar.MONTH, m)
                selectedCalendar.set(Calendar.DAY_OF_MONTH, d)
                updateDateButtonText()
            }, year, month, day).show()
        }

        binding.btnSelectTime.setOnClickListener {
            val hour = selectedCalendar.get(Calendar.HOUR_OF_DAY)
            val minute = selectedCalendar.get(Calendar.MINUTE)
            val second = selectedCalendar.get(Calendar.SECOND)
            TimePickerDialog(this, { _, h, min ->
                selectedCalendar.set(Calendar.HOUR_OF_DAY, h)
                selectedCalendar.set(Calendar.MINUTE, min)
                selectedCalendar.set(Calendar.SECOND, second)
                updateTimeButtonText()
            }, hour, minute, true).show()
        }

        binding.btnStartTask.setOnClickListener {
            startTask()
        }

        binding.btnCancelTask.setOnClickListener {
            stopService(Intent(this, ScheduledClickService::class.java).apply {
                action = ScheduledClickService.ACTION_CANCEL_TASK
            })
            updateRunningState(false)
        }

        binding.btnClearLog.setOnClickListener {
            binding.tvLiveLog.text = ""
        }
    }

    private fun startTask() {
        if (!PermissionHelper.isAccessibilityEnabled(this)) {
            Toast.makeText(this, "請先在無障礙設定中開啟「定時自動點擊輔助服務」！", Toast.LENGTH_LONG).show()
            PermissionHelper.openAccessibilitySettings(this)
            return
        }

        val targetMillis = resolveTargetTimeMillis()
        if (targetMillis == null || targetMillis <= System.currentTimeMillis()) {
            Toast.makeText(this, "目標時間無效或已早於當前時間！", Toast.LENGTH_SHORT).show()
            return
        }

        val x = binding.etCoordX.text.toString().toFloatOrNull() ?: 0f
        val y = binding.etCoordY.text.toString().toFloatOrNull() ?: 0f
        val repeatCount = binding.etRepeatCount.text.toString().toIntOrNull() ?: 1
        val repeatInterval = binding.etRepeatInterval.text.toString().toLongOrNull() ?: 100L
        val pressDuration = binding.etPressDuration.text.toString().toLongOrNull() ?: 50L

        val task = ClickTask(
            targetTimeMillis = targetMillis,
            x = x,
            y = y,
            repeatCount = repeatCount,
            repeatIntervalMs = repeatInterval,
            durationMs = pressDuration
        )

        val serviceIntent = Intent(this, ScheduledClickService::class.java).apply {
            action = ScheduledClickService.ACTION_START_TASK
            putExtra(ScheduledClickService.EXTRA_TASK, task)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        updateRunningState(true)
        appendLog("任務設定成功，目標: ${TimeUtils.formatFullDateTime(targetMillis)}")
        Toast.makeText(this, "定時點擊服務已在後台待命！", Toast.LENGTH_SHORT).show()
    }

    private fun resolveTargetTimeMillis(): Long? {
        return when (binding.rgTimeMode.checkedRadioButtonId) {
            R.id.rbModeDateTime -> {
                val millis = binding.etMillisec.text.toString().toIntOrNull()?.coerceIn(0, 999) ?: 0
                selectedCalendar.set(Calendar.MILLISECOND, millis)
                selectedCalendar.timeInMillis
            }
            R.id.rbModeEpoch -> {
                val input = binding.etEpochInput.text.toString()
                TimeUtils.parseEpoch(input)
            }
            R.id.rbModeCountdown -> {
                val seconds = binding.etCountdownSeconds.text.toString().toLongOrNull() ?: 0L
                System.currentTimeMillis() + (seconds * 1000L)
            }
            else -> null
        }
    }

    private fun updateTimeTicker() {
        val now = System.currentTimeMillis()
        binding.tvCurrentDeviceTime.text = "目前系統時間：${TimeUtils.formatFullDateTime(now)}"

        val target = resolveTargetTimeMillis()
        if (target != null) {
            binding.tvTargetTimePreview.text = "預計觸發時間：${TimeUtils.formatFullDateTime(target)}"
            val remaining = target - now
            binding.tvRemainingCountdown.text = "距離觸發剩餘：${TimeUtils.formatRemaining(remaining)}"
        } else {
            binding.tvTargetTimePreview.text = "預計觸發時間：(格式無效)"
            binding.tvRemainingCountdown.text = "距離觸發剩餘：--"
        }
    }

    private fun updateDateButtonText() {
        binding.btnSelectDate.text = TimeUtils.formatDate(selectedCalendar.timeInMillis)
    }

    private fun updateTimeButtonText() {
        binding.btnSelectTime.text = TimeUtils.formatTime(selectedCalendar.timeInMillis)
    }

    private fun updateRunningState(isRunning: Boolean) {
        if (isRunning) {
            binding.btnStartTask.isEnabled = false
            binding.btnStartTask.text = "任務進行中（後台待命中）"
            binding.btnCancelTask.visibility = View.VISIBLE
        } else {
            binding.btnStartTask.isEnabled = true
            binding.btnStartTask.text = getString(R.string.btn_start_task)
            binding.btnCancelTask.visibility = View.GONE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun refreshPermissionStatus() {
        val isA11yOk = PermissionHelper.isAccessibilityEnabled(this)
        binding.btnPermAccessibility.text = if (isA11yOk) "已開啟 ✔" else "未開啟 (點擊授權)"
        binding.btnPermAccessibility.setTextColor(
            ContextCompat.getColor(this, if (isA11yOk) R.color.status_green else R.color.status_red)
        )

        val isOverlayOk = PermissionHelper.canDrawOverlays(this)
        binding.btnPermOverlay.text = if (isOverlayOk) "已開啟 ✔" else "未開啟 (點擊授權)"
        binding.btnPermOverlay.setTextColor(
            ContextCompat.getColor(this, if (isOverlayOk) R.color.status_green else R.color.status_red)
        )

        val isAlarmOk = PermissionHelper.canScheduleExactAlarms(this)
        binding.btnPermExactAlarm.text = if (isAlarmOk) "已開啟 ✔" else "未開啟 (點擊授權)"
        binding.btnPermExactAlarm.setTextColor(
            ContextCompat.getColor(this, if (isAlarmOk) R.color.status_green else R.color.status_red)
        )
    }

    private fun appendLog(line: String) {
        val current = binding.tvLiveLog.text.toString()
        binding.tvLiveLog.text = "$current$line\n"
        binding.tvLiveLog.post {
            val scrollAmount = binding.tvLiveLog.layout?.let {
                it.getLineTop(binding.tvLiveLog.lineCount) - binding.tvLiveLog.height
            } ?: 0
            if (scrollAmount > 0) {
                binding.tvLiveLog.scrollTo(0, scrollAmount)
            }
        }
    }
}
