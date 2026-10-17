package com.xiaoyu.weread

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.res.Configuration
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persistent (user-authorized) MediaProjection session.
 *
 * Android 14+ does not let apps silently request a fresh MediaProjection for
 * every question. We request once on start, then sample frames when clicked.
 */
class OverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var overlayParams: WindowManager.LayoutParams
    private lateinit var titleLabel: TextView
    private lateinit var resultLabel: TextView
    private lateinit var button: Button
    private lateinit var ocrToggle: TextView
    private lateinit var ocrPanel: ScrollView
    private lateinit var ocrText: TextView
    private var latestOcrText = "尚未截图，请先点击获取答案"
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var capturePending = false // Only accessed on capture thread.
    private var screenshot: Bitmap? = null // Only accessed on capture thread.
    private val busy = AtomicBoolean(false)
    private val deepseek = DeepSeekClient()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_APPEARANCE) {
            if (isRunning) refreshOverlayAppearance()
            return START_NOT_STICKY
        }
        if (isRunning) return START_NOT_STICKY
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        if (data == null || code == 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, foregroundNotification())
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(code, data)
            windows = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            createProjection()
            showOverlay()
            isRunning = true
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "微信读书答题助手", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun foregroundNotification(): Notification {
        val launch = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("每日一答悬浮窗已开启")
            .setContentText("点击返回设置，可关闭悬浮窗")
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
    }

    private fun createProjection() {
        val thread = HandlerThread("WeReadScreenCapture")
        thread.start()
        captureThread = thread
        val handler = Handler(thread.looper)
        captureHandler = handler
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windows.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        reader!!.setOnImageAvailableListener({ incoming ->
            val image = incoming.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (capturePending) {
                    val plane = image.planes[0]
                    val paddedWidth = plane.rowStride / plane.pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                    plane.buffer.rewind()
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val bitmap = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                    padded.recycle()
                    screenshot?.recycle()
                    screenshot = bitmap
                }
            } catch (_: Exception) {
                // Keep the current candidate frame; the timeout handles no frames.
            } finally {
                image.close()
            }
        }, handler)

        val projectionRef = projection ?: error("MediaProjection permission missing")
        projectionRef.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                mainHandler.post { stopSelf() }
            }
        }, handler)
        virtualDisplay = projectionRef.createVirtualDisplay(
            "WeReadScreen", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun showOverlay() {
        // Normal mode occupies exactly two rows: draggable status and action.
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(7), dp(9), dp(9))
            elevation = dp(10).toFloat()
        }
        val firstRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(34)
        }
        titleLabel = TextView(this).apply {
            text = "⠿"
            textSize = 22f
            gravity = Gravity.CENTER
            contentDescription = "拖动悬浮窗"
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(34))
        }
        firstRow.addView(titleLabel)
        resultLabel = TextView(this).apply {
            text = "等待获取答案"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f)
        }
        firstRow.addView(resultLabel)
        root.addView(firstRow)

        button = Button(this).apply {
            text = "获取答案"
            isAllCaps = false
            textSize = 14f
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(dp(134), dp(38)).apply {
                topMargin = dp(3)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setOnClickListener {
                Appearance.tap(it)
                captureCurrentQuestion()
            }
        }
        root.addView(button)

        // Debug controls are invisible in the normal two-row layout.
        ocrToggle = TextView(this).apply {
            text = "查看图片理解文本 ▾"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(2))
            visibility = View.GONE
            setOnClickListener {
                Appearance.tap(it)
                toggleOcrPanel()
            }
        }
        root.addView(ocrToggle)
        ocrText = TextView(this).apply {
            text = latestOcrText
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        ocrPanel = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = false
            layoutParams = LinearLayout.LayoutParams(-1, dp(230)).apply {
                topMargin = dp(8)
            }
            addView(ocrText)
        }
        root.addView(ocrPanel)
        overlayParams = WindowManager.LayoutParams(
            dp(220), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(170)
        }
        titleLabel.setOnTouchListener(object : View.OnTouchListener {
            private var lastX = 0f
            private var lastY = 0f
            private var initialX = 0
            private var initialY = 0
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Appearance.tap(view)
                        lastX = event.rawX
                        lastY = event.rawY
                        initialX = overlayParams.x
                        initialY = overlayParams.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        overlayParams.x = (initialX - (event.rawX - lastX).toInt()).coerceAtLeast(0)
                        overlayParams.y = (initialY + (event.rawY - lastY).toInt()).coerceAtLeast(0)
                        windows.updateViewLayout(root, overlayParams)
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
                }
                return true
            }
        })
        windows.addView(root, overlayParams)
        refreshOverlayAppearance()
    }

    private fun refreshOverlayAppearance() {
        if (!::root.isInitialized) return
        val c = Appearance.resolve(this)
        root.background = rounded(c.surface, 15)
        titleLabel.setTextColor(c.accent)
        resultLabel.setTextColor(c.heading)
        button.setTextColor(c.accentText)
        button.background = rounded(c.accent, 10)
        ocrToggle.setTextColor(c.accent)
        ocrPanel.background = rounded(c.panel, 8)
        ocrText.setTextColor(c.text)
        val debugEnabled = SecureConfig(this).showImageDebug
        ocrToggle.visibility = if (debugEnabled) View.VISIBLE else View.GONE
        if (!debugEnabled) ocrPanel.visibility = View.GONE
        if (::windows.isInitialized) {
            overlayParams.width = dp(if (debugEnabled && ocrPanel.visibility == View.VISIBLE) 312 else 220)
            windows.updateViewLayout(root, overlayParams)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.post { if (isRunning) refreshOverlayAppearance() }
    }

    private fun toggleOcrPanel() {
        if (!SecureConfig(this).showImageDebug) return
        val expanded = ocrPanel.visibility != View.VISIBLE
        ocrPanel.visibility = if (expanded) View.VISIBLE else View.GONE
        ocrToggle.text = if (expanded) "收起图片理解文本 ▴" else "查看图片理解文本 ▾"
        overlayParams.width = dp(if (expanded) 312 else 220)
        windows.updateViewLayout(root, overlayParams)
    }

    private fun updateOcrDiagnostic(text: String) {
        latestOcrText = text
        if (::ocrText.isInitialized) {
            ocrText.text = text
            ocrPanel.scrollTo(0, 0)
        }
    }

    private fun captureCurrentQuestion() {
        if (!busy.compareAndSet(false, true)) return
        button.isEnabled = false
        resultLabel.text = "正在截图…"
        updateOcrDiagnostic("正在截取本次屏幕画面……")
        val handler = captureHandler ?: run { fail("录屏未启动"); return }
        handler.post {
            screenshot?.recycle()
            screenshot = null
            capturePending = true
            mainHandler.post { root.visibility = View.GONE }
            // Keep latest projection frame after the overlay disappears.
            handler.postDelayed({
                capturePending = false
                val frame = screenshot
                screenshot = null
                mainHandler.post {
                    root.visibility = View.VISIBLE
                    if (frame == null) {
                        updateOcrDiagnostic("截图失败：未获取到屏幕画面。请检查是否授权整个屏幕。")
                        fail("截图失败：请确保授权的是整个屏幕")
                    } else requestAnswer(frame)
                }
            }, 550)
        }
    }

    private fun requestAnswer(bitmap: Bitmap) {
        val config = SecureConfig(this)
        val key = config.loadKey()
        if (key.isBlank()) {
            bitmap.recycle()
            fail("请在主界面填写 DeepSeek API Key")
            return
        }
        resultLabel.text = "DeepSeek 图片识别中…"
        updateOcrDiagnostic("已截取屏幕（${bitmap.width}×${bitmap.height}），正在压缩图片并发送给 DeepSeek 识别……")
        deepseek.ask(
            bitmap,
            key,
            config.endpoint,
            config.visionModel,
            config.temperature,
            config.searchCount,
            onVision = { parsed ->
                mainHandler.post {
                    updateOcrDiagnostic(parsed)
                    resultLabel.text = "DeepSeek 搜索中…"
                }
            },
            onSearching = {
                mainHandler.post { resultLabel.text = "Flash 判断中…" }
            }
        ) { result ->
            mainHandler.post {
                result.fold(
                    onSuccess = { answer ->
                        resultLabel.text = "第 ${answer.index} 个 · ${answer.elapsedMs}ms"
                        resultLabel.contentDescription = "${answer.optionCount}选1，答案第${answer.index}个，耗时${answer.elapsedMs}毫秒，已联网"
                        finishBusy()
                    },
                    onFailure = { fail(it.message ?: "DeepSeek 请求失败") }
                )
            }
        }
    }

    private fun fail(message: String) {
        resultLabel.text = "获取失败"
        resultLabel.text = "获取失败，点此重试"
        resultLabel.contentDescription = message
        android.widget.Toast.makeText(this, message.take(180), android.widget.Toast.LENGTH_LONG).show()
        finishBusy()
    }

    private fun finishBusy() {
        button.isEnabled = true
        busy.set(false)
    }

    override fun onDestroy() {
        isRunning = false
        try {
            if (::windows.isInitialized && ::root.isInitialized) windows.removeView(root)
        } catch (_: Exception) {}
        // Release all projection objects. Stop callback may run again and is harmless.
        try { reader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { screenshot?.recycle() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        try { captureThread?.quitSafely() } catch (_: Exception) {}
        deepseek.close()
        super.onDestroy()
    }

    companion object {
        const val ACTION_APPEARANCE = "com.xiaoyu.weread.UPDATE_APPEARANCE"
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"
        private const val CHANNEL_ID = "weread_projection"
        private const val NOTIFICATION_ID = 101
        @Volatile var isRunning: Boolean = false
            private set
    }
}
