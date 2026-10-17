package com.xiaoyu.weread

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var config: SecureConfig
    private lateinit var keyInput: EditText
    private lateinit var endpointInput: EditText
    private lateinit var visionInput: EditText
    private lateinit var searchInput: EditText
    private lateinit var temperatureInput: EditText
    private lateinit var reasoningInput: Spinner
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var testButton: Button
    private val client = DeepSeekClient()
    private var waitingForOverlayPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = SecureConfig(this)
        buildUi()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun background(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun field(hint: String, value: String): EditText = EditText(this).apply {
        setSingleLine(true)
        setText(value)
        setHint(hint)
        textSize = 15f
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = background(Color.WHITE, 11)
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) }
    }
    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(Color.rgb(85, 99, 114))
        setPadding(0, dp(9), 0, dp(7))
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(244, 248, 252)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(44), dp(24), dp(24))
        }
        scroll.addView(content)
        content.addView(TextView(this).apply {
            text = "微信读书 · 每日一答"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(22, 45, 68))
        })
        content.addView(TextView(this).apply {
            text = "截图交给 DeepSeek 看图，服务器联网搜索答案"
            textSize = 14f
            setTextColor(Color.rgb(100, 121, 139))
            setPadding(0, dp(10), 0, dp(22))
        })
        content.addView(label("DeepSeek 官方 API Key"))
        keyInput = field("输入 DeepSeek API Key", config.loadKey()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        content.addView(keyInput)
        val advanced = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        content.addView(Button(this).apply {
            text = "高级配置（接口 / 模型 / 参数） ▾"
            isAllCaps = false
            setOnClickListener {
                advanced.visibility = if (advanced.visibility == View.GONE) View.VISIBLE else View.GONE
            }
        })
        advanced.addView(label("DeepSeek 接口根地址（HTTPS）"))
        endpointInput = field(SecureConfig.DEFAULT_ENDPOINT, config.endpoint)
        advanced.addView(endpointInput)
        advanced.addView(label("图片理解模型"))
        visionInput = field(SecureConfig.DEFAULT_VISION_MODEL, config.visionModel)
        advanced.addView(visionInput)
        advanced.addView(label("联网搜索模型"))
        searchInput = field(SecureConfig.DEFAULT_SEARCH_MODEL, config.searchModel)
        advanced.addView(searchInput)
        advanced.addView(label("图片识别温度（0～2）"))
        temperatureInput = field("0.2", config.temperature.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        advanced.addView(temperatureInput)
        advanced.addView(label("联网搜索推理等级"))
        reasoningInput = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, SecureConfig.REASONING_OPTIONS)
            setSelection(SecureConfig.REASONING_OPTIONS.indexOf(config.reasoningEffort).coerceAtLeast(0))
        }
        advanced.addView(reasoningInput)
        advanced.addView(label("联网搜索始终开启；无真实搜索结果不会显示答案"))
        content.addView(advanced)
        testButton = Button(this).apply {
            text = "测试 API（看图 + 联网搜索）"
            isAllCaps = false
            setOnClickListener { testApi() }
        }
        content.addView(testButton)
        toggle = Button(this).apply {
            isAllCaps = false
            textSize = 18f
            setTextColor(Color.WHITE)
            background = background(Color.rgb(20, 129, 231), 14)
            layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(26) }
            setOnClickListener {
                if (OverlayService.isRunning) {
                    stopService(Intent(this@MainActivity, OverlayService::class.java))
                    Handler(Looper.getMainLooper()).postDelayed({ refreshState() }, 200)
                } else saveAndStart()
            }
        }
        content.addView(toggle)
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(95, 115, 131))
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(14))
        }
        content.addView(status)
        content.addView(TextView(this).apply {
            text = "首次开启需授权悬浮窗和录制整个屏幕；截图在本机压缩成 JPEG，然后发送给 DeepSeek 官方 API。图片可能包含个人信息，请注意屏幕内容。"
            textSize = 13f
            setTextColor(Color.rgb(105, 122, 140))
        })
        setContentView(scroll)
        refreshState()
    }

    private fun saveSettings(): Boolean {
        if (keyInput.text.isNullOrBlank()) {
            status.text = "请先填写 DeepSeek API Key"
            return false
        }
        val endpoint = endpointInput.text.toString().trim()
        if (!endpoint.startsWith("https://", true)) {
            status.text = "接口地址必须为 HTTPS"
            return false
        }
        if (visionInput.text.isNullOrBlank() || searchInput.text.isNullOrBlank()) {
            status.text = "请填写图片模型和联网模型"
            return false
        }
        val temperature = temperatureInput.text.toString().toDoubleOrNull()
        if (temperature == null || temperature !in 0.0..2.0) {
            status.text = "温度需在 0～2 之间"
            return false
        }
        try {
            config.saveKey(keyInput.text.toString())
            config.endpoint = endpoint
            config.visionModel = visionInput.text.toString()
            config.searchModel = searchInput.text.toString()
            config.temperature = temperature
            config.reasoningEffort = reasoningInput.selectedItem.toString()
        } catch (_: Exception) {
            status.text = "密钥保存失败，请检查设备安全设置"
            return false
        }
        return true
    }

    private fun testApi() {
        if (!saveSettings()) return
        testButton.isEnabled = false
        status.text = "正在测试图片识别和服务器端联网搜索（消耗 API 额度）…"
        val bitmap = Bitmap.createBitmap(780, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(9, 38, 71))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 42f }
        canvas.drawText("中国的首都是哪里？", 48f, 120f, paint)
        canvas.drawText("北京", 80f, 260f, paint)
        canvas.drawText("上海", 80f, 380f, paint)
        client.ask(bitmap, config.loadKey(), config.endpoint, config.visionModel, config.searchModel,
            config.temperature, config.reasoningEffort, onVision = {}) { result ->
            runOnUiThread {
                if (!isDestroyed) {
                    testButton.isEnabled = true
                    result.fold(
                        onSuccess = { answer ->
                            status.text = if (answer.index == 1)
                                "两阶段测试通过 · 已联网 · ${answer.elapsedMs} ms"
                            else "API 已连接，但测试答案不符：第 ${answer.index} 项"
                        },
                        onFailure = { status.text = "测试失败：${it.message ?: "未知错误"}" }
                    )
                }
            }
        }
    }

    private fun saveAndStart() {
        if (!saveSettings()) return
        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true
            status.text = "请授权悬浮窗权限"
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        requestScreenCapture()
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({
            refreshState()
            if (waitingForOverlayPermission && Settings.canDrawOverlays(this)) {
                waitingForOverlayPermission = false
                requestScreenCapture()
            }
        }, 250)
    }

    private fun requestScreenCapture() {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        status.text = "请授权录制整个屏幕"
        startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Compatible with Android 8 through current SDK.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            status.text = "屏幕捕获未授权"
            return
        }
        val service = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            status.text = "悬浮窗启动中…"
            Handler(Looper.getMainLooper()).postDelayed({ refreshState() }, 900)
        } catch (error: Exception) {
            status.text = "启动失败：${error.message ?: "系统限制"}"
        }
    }

    private fun refreshState() {
        toggle.text = if (OverlayService.isRunning) "关闭悬浮窗" else "开启悬浮窗"
        if (OverlayService.isRunning) status.text = "已开启，请切换微信读书"
    }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }

    companion object { private const val REQUEST_CAPTURE = 1027 }
}
