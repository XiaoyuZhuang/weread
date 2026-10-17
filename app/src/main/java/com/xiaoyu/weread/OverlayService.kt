package com.xiaoyu.weread

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
    private lateinit var resultLabel: TextView
    private lateinit var detailLabel: TextView
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
    private val ocr = OcrEngine()
    private val groq = GroqClient()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(10), dp(13), dp(11))
            background = rounded(Color.rgb(17, 36, 54), 17)
            elevation = dp(10).toFloat()
        }
        val title = TextView(this).apply {
            text = "≡  每日一答"
            textSize = 13f
            setTextColor(Color.rgb(151, 197, 236))
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(title)
        resultLabel = TextView(this).apply {
            text = "等待获取答案"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(5))
        }
        root.addView(resultLabel)
        detailLabel = TextView(this).apply {
            text = "按下方按钮截取当前题目"
            textSize = 11f
            setTextColor(Color.rgb(178, 201, 221))
            gravity = Gravity.CENTER
        }
        root.addView(detailLabel)
        button = Button(this).apply {
            text = "获取答案"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(20, 135, 229), 12)
            layoutParams = LinearLayout.LayoutParams(dp(172), dp(47)).apply {
                topMargin = dp(9)
            }
            setOnClickListener { captureCurrentQuestion() }
        }
        root.addView(button)

        // Diagnostic view only; hidden by default and does not change the
        // capture, OCR, or Groq search behavior. Text remains visible on API 400.
        ocrToggle = TextView(this).apply {
            text = "查看OCR识别文本 ▾"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(170, 209, 238))
            setPadding(0, dp(7), 0, dp(2))
            setOnClickListener { toggleOcrPanel() }
        }
        root.addView(ocrToggle)
        ocrText = TextView(this).apply {
            text = latestOcrText
            textSize = 13f
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        ocrPanel = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = false
            background = rounded(Color.rgb(36, 57, 76), 8)
            layoutParams = LinearLayout.LayoutParams(-1, dp(230)).apply {
                topMargin = dp(8)
            }
            addView(ocrText)
        }
        root.addView(ocrPanel)

        overlayParams = WindowManager.LayoutParams(
            dp(198), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(170)
        }
        title.setOnTouchListener(object : View.OnTouchListener {
            private var lastX = 0f
            private var lastY = 0f
            private var initialX = 0
            private var initialY = 0
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.rawX
                        lastY = event.rawY
                        initialX = overlayParams.x
                        initialY = overlayParams.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        overlayParams.x = (initialX - (event.rawX - lastX).toInt())
                            .coerceAtLeast(0)
                        overlayParams.y = (initialY + (event.rawY - lastY).toInt())
                            .coerceAtLeast(0)
                        windows.updateViewLayout(root, overlayParams)
                        return true
                    }
                }
                return true
            }
        })
        windows.addView(root, overlayParams)
    }

    private fun toggleOcrPanel() {
        val expanded = ocrPanel.visibility != View.VISIBLE
        ocrPanel.visibility = if (expanded) View.VISIBLE else View.GONE
        ocrToggle.text = if (expanded) "收起OCR识别文本 ▴" else "查看OCR识别文本 ▾"
        overlayParams.width = dp(if (expanded) 312 else 198)
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
        detailLabel.text = "稍等片刻"
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
                    } else recognize(frame)
                }
            }, 550)
        }
    }

    private fun recognize(bitmap: Bitmap) {
        resultLabel.text = "识别文字中…"
        updateOcrDiagnostic("已获取屏幕画面（${bitmap.width}×${bitmap.height}），正在本地OCR识别……")
        ocr.extract(bitmap) { result ->
            mainHandler.post {
                result.fold(
                    onSuccess = { text ->
                        updateOcrDiagnostic(if (text.isBlank()) "截图成功，但OCR未识别出文字" else text)
                        if (text.length < 8) {
                            fail("题目文字太少，请重试")
                        } else {
                            requestAnswer(text)
                        }
                    },
                    onFailure = {
                        updateOcrDiagnostic("截图成功，但OCR失败：${it.message ?: "未知错误"}")
                        fail("OCR 失败：${it.message ?: "未知错误"}")
                    }
                )
            }
        }
    }

    private fun requestAnswer(text: String) {
        resultLabel.text = if (SecureConfig(this).browserSearch) "Groq 联网搜索中…" else "Groq 答题中…"
        val config = SecureConfig(this)
        val key = config.loadKey()
        if (key.isBlank()) {
            fail("API Key 为空，请返回主界面填写")
            return
        }
        groq.ask(text, key, config.endpoint, config.model, config.browserSearch) { result ->
            mainHandler.post {
                result.fold(
                    onSuccess = { answer ->
                        resultLabel.text = "第 ${answer.index} 个"
                        detailLabel.text = buildString {
                            append("${answer.optionCount} 选 1 · ${answer.elapsedMs} ms")
                        }
                        finishBusy()
                    },
                    onFailure = { fail(it.message ?: "请求失败") }
                )
            }
        }
    }

    private fun fail(message: String) {
        resultLabel.text = "获取失败"
        detailLabel.text = message.take(65)
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
        ocr.close()
        groq.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"
        private const val CHANNEL_ID = "weread_projection"
        private const val NOTIFICATION_ID = 101
        @Volatile var isRunning: Boolean = false
            private set
    }
}
